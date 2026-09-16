package com.radialtiles.data.server

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import com.radialtiles.data.api.HomeAssistantClient
import com.radialtiles.data.model.AppConfiguration
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

class WebConfigServer(
    private val context: Context? = null,
    private val port: Int = 8080,
    private val getCurrentConfig: () -> AppConfiguration,
    private val onConfigSaved: (AppConfiguration) -> Unit
) {
    companion object {
        private const val TAG = "WebConfigServer"
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun start() {
        if (serverJob?.isActive == true) return

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "Config server started on port $port")

                while (isActive) {
                    val clientSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        break
                    }
                    launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in WebConfigServer", e)
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val output = BufferedOutputStream(s.getOutputStream())

                val requestLine = reader.readLine() ?: return@use
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@use
                val method = parts[0]
                val path = parts[1]

                // Read headers
                val headers = mutableMapOf<String, String>()
                var line: String?
                var contentLength = 0
                while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                    val headerParts = line!!.split(":", limit = 2)
                    if (headerParts.size == 2) {
                        val headerName = headerParts[0].trim().lowercase()
                        val headerVal = headerParts[1].trim()
                        headers[headerName] = headerVal
                        if (headerName == "content-length") {
                            contentLength = headerVal.toIntOrNull() ?: 0
                        }
                    }
                }

                // Read body if POST
                val body = if (contentLength > 0) {
                    val charBuffer = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val r = reader.read(charBuffer, readTotal, contentLength - readTotal)
                        if (r == -1) break
                        readTotal += r
                    }
                    String(charBuffer, 0, readTotal)
                } else {
                    ""
                }

                // Route request
                when {
                    method == "GET" && (path == "/" || path.startsWith("/index")) -> {
                        sendResponse(output, 200, "text/html; charset=utf-8", PortalHtml.getHtml(context).toByteArray())
                    }
                    method == "GET" && path == "/api/config" -> {
                        val configJson = json.encodeToString(getCurrentConfig())
                        sendResponse(output, 200, "application/json", configJson.toByteArray())
                    }
                    method == "POST" && path == "/api/save_config" -> {
                        try {
                            val newConfig = json.decodeFromString<AppConfiguration>(body)
                            onConfigSaved(newConfig)
                            sendResponse(output, 200, "application/json", """{"success": true}""".toByteArray())
                        } catch (e: Exception) {
                            sendResponse(output, 400, "application/json", """{"success": false, "error": "${e.message}"}""".toByteArray())
                        }
                    }
                    method == "POST" && path == "/api/test_ha" -> {
                        try {
                            val obj = json.parseToJsonElement(body).jsonObject
                            val url = obj["url"]?.jsonPrimitive?.content ?: ""
                            val localUrl = obj["localUrl"]?.jsonPrimitive?.content ?: ""
                            val token = obj["token"]?.jsonPrimitive?.content ?: ""

                            Log.d(TAG, "Testing HA connection: remote=$url, local=$localUrl")
                            val client = HomeAssistantClient(
                                getBaseUrl = { url },
                                getLocalUrl = { localUrl },
                                getToken = { token }
                            )
                            val states = client.fetchAllStates()
                            val entitiesJson = buildJsonObject {
                                put("success", states.isNotEmpty())
                                put("message", if (states.isNotEmpty()) "Loaded ${states.size} entities" else "Connected, but 0 entities returned")
                                put("entities", buildJsonArray {
                                    states.forEach { state ->
                                        add(buildJsonObject {
                                            put("entity_id", state.entity_id)
                                            put("friendly_name", state.friendlyName)
                                            put("state", state.state)
                                        })
                                    }
                                })
                            }
                            sendResponse(output, 200, "application/json", entitiesJson.toString().toByteArray())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error testing HA in WebConfigServer", e)
                            val errJson = buildJsonObject {
                                put("success", false)
                                put("message", "Error: ${e.message ?: e.javaClass.simpleName}")
                            }
                            sendResponse(output, 200, "application/json", errJson.toString().toByteArray())
                        }
                    }
                    else -> {
                        sendResponse(output, 404, "text/plain", "Not Found".toByteArray())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception handling socket", e)
        }
    }

    private fun sendResponse(out: OutputStream, code: Int, contentType: String, data: ByteArray) {
        val statusText = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Internal Error"
        }
        val header = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(data)
        out.flush()
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverJob?.cancel()
        serverSocket = null
    }

    /**
     * Helper to get device local Wi-Fi IP address.
     */
    fun getLocalIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                @Suppress("DEPRECATION")
                return Formatter.formatIpAddress(ipInt)
            }

            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val sAddr = addr.hostAddress ?: ""
                        if (!sAddr.contains(":")) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP", e)
        }
        return "127.0.0.1"
    }
}
