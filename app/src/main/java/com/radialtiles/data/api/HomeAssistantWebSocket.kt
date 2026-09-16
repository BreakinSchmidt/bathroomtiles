package com.radialtiles.data.api

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HomeAssistantWebSocket(
    private val getBaseUrl: () -> String,
    private val getToken: () -> String,
    private val onStateChanged: (entityId: String, newState: String) -> Unit
) {
    companion object {
        private const val TAG = "HAWebSocket"

        private val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })

        private val sslSocketFactory = javax.net.ssl.SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, java.security.SecureRandom())
        }.socketFactory
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private val messageIdCounter = AtomicInteger(1)
    private var isConnected = false
    private var reconnectJob: Job? = null

    private val client = OkHttpClient.Builder()
        .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect() {
        disconnect()
        val rawBase = getBaseUrl().trim().trimEnd('/')
        val token = getToken().trim()
        if (rawBase.isBlank() || token.isBlank()) return

        val wsUrl = when {
            rawBase.startsWith("https://") -> rawBase.replace("https://", "wss://") + "/api/websocket"
            rawBase.startsWith("http://") -> rawBase.replace("http://", "ws://") + "/api/websocket"
            else -> "wss://$rawBase/api/websocket"
        }

        Log.d(TAG, "Connecting WebSocket to $wsUrl")
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened, awaiting auth_required")
                isConnected = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(webSocket, text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                isConnected = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                isConnected = false
                scheduleReconnect()
            }
        })
    }

    private fun handleMessage(ws: WebSocket, text: String) {
        try {
            val element = json.parseToJsonElement(text).jsonObject
            val type = element["type"]?.jsonPrimitive?.content ?: return

            when (type) {
                "auth_required" -> {
                    val authMsg = buildJsonObject {
                        put("type", "auth")
                        put("access_token", getToken())
                    }
                    ws.send(authMsg.toString())
                }
                "auth_ok" -> {
                    Log.d(TAG, "WebSocket Authenticated successfully! Subscribing to state_changed events...")
                    val id = messageIdCounter.getAndIncrement()
                    val subMsg = buildJsonObject {
                        put("id", id)
                        put("type", "subscribe_events")
                        put("event_type", "state_changed")
                    }
                    ws.send(subMsg.toString())
                }
                "auth_invalid" -> {
                    Log.e(TAG, "WebSocket Auth failed: ${element["message"]}")
                }
                "event" -> {
                    val eventObj = element["event"]?.jsonObject ?: return
                    val dataObj = eventObj["data"]?.jsonObject ?: return
                    val entityId = dataObj["entity_id"]?.jsonPrimitive?.content ?: return
                    val newStateObj = dataObj["new_state"]?.jsonObject
                    val state = newStateObj?.get("state")?.jsonPrimitive?.content ?: "off"

                    onStateChanged(entityId, state)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling WS message", e)
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5000)
            if (!isConnected) {
                connect()
            }
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "App closed")
        webSocket = null
        isConnected = false
    }
}
