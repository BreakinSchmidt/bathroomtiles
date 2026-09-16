package com.radialtiles.data.server

import android.content.Context
import java.io.InputStreamReader

object PortalHtml {
    private var cachedHtml: String? = null

    fun getHtml(context: Context? = null): String {
        cachedHtml?.let { return it }

        if (context != null) {
            try {
                context.assets.open("portal.html").use { stream ->
                    val content = InputStreamReader(stream, Charsets.UTF_8).readText()
                    cachedHtml = content
                    return content
                }
            } catch (e: Exception) {
                // Fallback to basic HTML if asset reading fails
            }
        }

        return """
<!DOCTYPE html>
<html>
<head><meta name="viewport" content="width=device-width, initial-scale=1"><title>RadialTiles</title></head>
<body style="background:#09090b;color:#fff;font-family:sans-serif;padding:20px;text-align:center;">
  <h2>RadialTiles Configurator</h2>
  <p>Please ensure portal.html asset is present.</p>
</body>
</html>
        """.trimIndent()
    }
}
