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
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .build()

        private val fastLocalServiceClient = OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .retryOnConnectionFailure(false)
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .writeTimeout(3000, TimeUnit.MILLISECONDS)
            .build()

        private val serviceClient = OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .retryOnConnectionFailure(true)
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(4000, TimeUnit.MILLISECONDS)
            .writeTimeout(4000, TimeUnit.MILLISECONDS)
            .build()

        private val regularClient = OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .retryOnConnectionFailure(true)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun getCandidateBaseUrls(): List<String> {
        val remote = getBaseUrl().trim().trimEnd('/')
        val local = getLocalUrl().trim().trimEnd('/')
        val candidates = mutableListOf<String>()

        // Option A: Remote-First (Internet / Nabu Casa) for rock-solid reliability with zero timeouts everywhere
        if (remote.isNotBlank()) {
            candidates.add(remote)
        }
        if (local.isNotBlank() && !candidates.contains(local)) {
            candidates.add(local)
        }
        return candidates
    }

    /**
     * Determines active base URL: remote URL first; if not configured, uses local LAN URL.
     */
    suspend fun resolveActiveBaseUrl(): String = withContext(Dispatchers.IO) {
        val remote = getBaseUrl().trim().trimEnd('/')
        val local = getLocalUrl().trim().trimEnd('/')

        if (remote.isNotBlank()) {
            return@withContext remote
        }
        return@withContext local
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
            // Only attempt protocol fallback if the exception was specifically SSL/TLS handshake related.
            // Never retry on connection timeouts or host unreachable, which would uselessly double latency.
            val isSslFailure = e is javax.net.ssl.SSLException
            val altUrl = if (isSslFailure) {
                when {
                    targetUrl.startsWith("https://") -> targetUrl.replaceFirst("https://", "http://")
                    targetUrl.startsWith("http://") -> targetUrl.replaceFirst("http://", "https://")
                    else -> null
                }
            } else null

            if (altUrl != null) {
                Log.w(TAG, "Call to $targetUrl failed with SSL error (${e.message}), attempting fallback to $altUrl")
                client.newCall(buildReq(altUrl)).execute()
            } else {
                throw e
            }
        }
    }

    /**
     * Calls a service in Home Assistant (e.g. light.toggle, switch.toggle, scene.turn_on)
     * and verifies that the server responded with a valid HTTP 2xx payload.
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

        val localUrl = getLocalUrl().trim().trimEnd('/')
        val hasRemoteFallback = getBaseUrl().trim().trimEnd('/').isNotBlank()

        for (baseUrl in candidates) {
            val url = "$baseUrl/api/services/$domain/$service"
            // Use fast local client (1.5s timeout) if trying local LAN and remote fallback exists
            val client = if (baseUrl == localUrl && hasRemoteFallback) fastLocalServiceClient else serviceClient
            try {
                executeWithFallback(url, token, requestBody, client).use { response ->
                    if (response.isSuccessful) {
                        lastWorkingBaseUrl = baseUrl
                        Log.d(TAG, "HA server confirmed $domain.$service for $entityId via $baseUrl (HTTP ${response.code})")
                        return@withContext true
                    } else {
                        Log.e(
                            TAG,
                            "Call service unconfirmed via $baseUrl: HTTP ${response.code} ${response.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed calling $domain/$service for $entityId via $baseUrl: ${e.message}")
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
