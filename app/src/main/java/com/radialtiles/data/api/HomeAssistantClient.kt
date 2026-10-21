package com.radialtiles.data.api

import android.util.Log
import com.radialtiles.data.model.HaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HomeAssistantClient(
    private val getBaseUrl: () -> String,
    private val getLocalUrl: () -> String,
    private val getToken: () -> String
) {
    companion object {
        private const val TAG = "HomeAssistantClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        private val sslSocketFactory = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }.socketFactory

        @Volatile
        private var lastWorkingBaseUrl: String? = null

        private val fastCheckClient = OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .retryOnConnectionFailure(true)
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .build()

        private val serviceClient = OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .retryOnConnectionFailure(true)
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        private val regularClient = OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .retryOnConnectionFailure(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun getCandidateBaseUrls(): List<String> {
        val local = getLocalUrl().trim().trimEnd('/')
        val remote = getBaseUrl().trim().trimEnd('/')
        val candidates = mutableListOf<String>()

        val cached = lastWorkingBaseUrl
        if (!cached.isNullOrBlank() && (cached == local || cached == remote)) {
            candidates.add(cached)
        }
        if (local.isNotBlank() && !candidates.contains(local)) {
            candidates.add(local)
        }
        if (remote.isNotBlank() && !candidates.contains(remote)) {
            candidates.add(remote)
        }
        return candidates
    }

    /**
     * Determines active base URL: tries local URL first if provided; if unreachable, falls back to remote Nabu Casa URL.
     */
    suspend fun resolveActiveBaseUrl(): String = withContext(Dispatchers.IO) {
        val local = getLocalUrl().trim().trimEnd('/')
        val remote = getBaseUrl().trim().trimEnd('/')

        if (local.isNotBlank()) {
            if (remote.isBlank()) {
                return@withContext local
            }
            try {
                val req = Request.Builder()
                    .url("$local/api/")
                    .header("Authorization", "Bearer ${getToken()}")
                    .build()
                fastCheckClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Using ultra-low-latency local HA URL: $local")
                        lastWorkingBaseUrl = local
                        return@withContext local
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Local HA URL not reachable ($local): ${e.message}")
            }
        }

        return@withContext remote.ifBlank { local }
    }

    private fun executeWithFallback(
        targetUrl: String,
        token: String,
        postBody: RequestBody? = null,
        client: OkHttpClient = regularClient
    ): Response {
        val buildReq = { u: String ->
            val b = Request.Builder().url(u).header("Authorization", "Bearer $token")
            if (postBody != null) b.post(postBody) else b.get()
            b.build()
        }

        return try {
            client.newCall(buildReq(targetUrl)).execute()
        } catch (e: Exception) {
            // If https failed on a plaintext http port, try http
            val altUrl = when {
                targetUrl.startsWith("https://") -> targetUrl.replaceFirst("https://", "http://")
                targetUrl.startsWith("http://") -> targetUrl.replaceFirst("http://", "https://")
                else -> null
            }
            if (altUrl != null) {
                Log.w(TAG, "Call to $targetUrl failed (${e.message}), attempting fallback to $altUrl")
                client.newCall(buildReq(altUrl)).execute()
            } else {
                throw e
            }
        }
    }

    /**
     * Calls a service in Home Assistant (e.g. light.toggle, switch.toggle, scene.turn_on)
     * and verifies that the server responded with a valid HTTP 2xx JSON payload.
     */
    suspend fun callService(
        domain: String,
        service: String,
        entityId: String,
        extraData: Map<String, Any> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        val candidates = getCandidateBaseUrls()
        val token = getToken().trim()
        if (candidates.isEmpty() || token.isBlank()) {
            Log.w(TAG, "Cannot call service: missing baseUrl or token")
            return@withContext false
        }

        val payload = buildJsonObject {
            put("entity_id", entityId)
            extraData.forEach { (key, value) ->
                when (value) {
                    is Boolean -> put(key, value)
                    is Number -> put(key, value)
                    is String -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

        // Up to 2 passes across candidate URLs so a cold radio wake-up on the first press
        // automatically retries and succeeds without requiring a second button press.
        for (attempt in 1..2) {
            for (baseUrl in candidates) {
                val url = "$baseUrl/api/services/$domain/$service"
                try {
                    executeWithFallback(url, token, requestBody, serviceClient).use { response ->
                        val bodyText = response.body?.string()?.trim()
                        val hasValidServerPayload = bodyText != null &&
                            (bodyText.startsWith("[") || bodyText.startsWith("{"))
                        if (response.isSuccessful && hasValidServerPayload) {
                            lastWorkingBaseUrl = baseUrl
                            Log.d(TAG, "HA server confirmed $domain.$service for $entityId via $baseUrl (HTTP ${response.code})")
                            return@withContext true
                        } else {
                            Log.e(
                                TAG,
                                "Call service unconfirmed (attempt $attempt) via $baseUrl: HTTP ${response.code} ${response.message}, body=$bodyText"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Attempt $attempt failed calling $domain/$service for $entityId via $baseUrl: ${e.message}")
                }
            }
            if (attempt == 1) {
                delay(250L)
            }
        }

        return@withContext false
    }

    suspend fun toggleEntity(entityId: String, domain: String): Boolean {
        val service = when (domain) {
            "light", "switch" -> "toggle"
            "scene", "script" -> "turn_on"
            "automation" -> "trigger"
            else -> "toggle"
        }
        return callService(domain, service, entityId)
    }

    /**
     * Fetches states of all entities. Throws exception if failed so caller can see real error.
     */
    suspend fun fetchAllStates(): List<HaEntity> = withContext(Dispatchers.IO) {
        val baseUrl = resolveActiveBaseUrl()
        val token = getToken()
        if (baseUrl.isBlank() || token.isBlank()) return@withContext emptyList()

        val url = "$baseUrl/api/states"
        executeWithFallback(url, token).use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string() ?: return@withContext emptyList()
            return@withContext json.decodeFromString<List<HaEntity>>(body)
        }
    }

    suspend fun testEndpoint(urlToTest: String, tokenToTest: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val cleanUrl = urlToTest.trim().trimEnd('/')
            if (cleanUrl.isBlank() || tokenToTest.isBlank()) {
                return@withContext Pair(false, "URL or token is empty")
            }

            try {
                executeWithFallback("$cleanUrl/api/", tokenToTest).use { response ->
                    if (response.isSuccessful) {
                        return@withContext Pair(true, "Connected successfully to $cleanUrl")
                    } else {
                        return@withContext Pair(false, "HTTP ${response.code}: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                return@withContext Pair(false, e.localizedMessage ?: e.javaClass.simpleName)
            }
        }
}
