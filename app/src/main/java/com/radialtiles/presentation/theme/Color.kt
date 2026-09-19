package com.radialtiles.presentation.theme

import androidx.compose.ui.graphics.Color

val Black = Color(0xFF000000)
val DarkBackground = Color(0xFF0C0C0E)
val SurfaceDark = Color(0xFF18181B)
val BorderDark = Color(0x33FFFFFF)

// Distinct customizable accent colors
val AmberAccent = Color(0xFFFFB300)
val GreenAccent = Color(0xFF00E676)
val CyanAccent = Color(0xFF00E5FF)
val CoralAccent = Color(0xFFFF5252)
val VioletAccent = Color(0xFF7C4DFF)
val BlueAccent = Color(0xFF448AFF)
val PinkAccent = Color(0xFFFF4081)
val OrangeAccent = Color(0xFFFF9100)

fun parseHexColor(hex: String, fallback: Color = AmberAccent): Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = when (clean.length) {
            6 -> ("FF$clean").toLong(16)
            8 -> clean.toLong(16)
            else -> return fallback
        }
        Color(colorInt)
    } catch (e: Exception) {
        fallback
    }
}

/**
 * Converts RGB components [0f..1f] to HSL (H in [0..360], S in [0..1], L in [0..1]).
 */
fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val l = (max + min) / 2f
    val h: Float
    val s: Float

    if (delta == 0f) {
        h = 0f
        s = 0f
    } else {
        s = if (l <= 0.5f) delta / (max + min) else delta / (2f - max - min)
        h = when (max) {
            r -> ((g - b) / delta + (if (g < b) 6f else 0f)) * 60f
            g -> ((b - r) / delta + 2f) * 60f
            else -> ((r - g) / delta + 4f) * 60f
        }
    }
    return floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

/**
 * Converts HSL components (H in [0..360], S in [0..1], L in [0..1]) to RGB [0f..1f].
 */
fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
    if (s == 0f) {
        return floatArrayOf(l, l, l)
    }
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    val hk = (h % 360f + 360f) % 360f / 360f

    fun hueToRgb(t: Float): Float {
        var tc = t
        if (tc < 0f) tc += 1f
        if (tc > 1f) tc -= 1f
        return when {
            tc < 1f / 6f -> p + (q - p) * 6f * tc
            tc < 1f / 2f -> q
            tc < 2f / 3f -> p + (q - p) * (2f / 3f - tc) * 6f
            else -> p
        }
    }

    return floatArrayOf(
        hueToRgb(hk + 1f / 3f).coerceIn(0f, 1f),
        hueToRgb(hk).coerceIn(0f, 1f),
        hueToRgb(hk - 1f / 3f).coerceIn(0f, 1f)
    )
}

/**
 * Computes a brighter, vivid version of [baseColor] by boosting lightness and vibrancy in HSL.
 */
fun computeBrightenedColor(baseColor: Color): Color {
    val hsl = rgbToHsl(baseColor.red, baseColor.green, baseColor.blue)
    val h = hsl[0]
    var s = hsl[1]
    val currentL = hsl[2]

    // Boost lightness into a vivid, illuminated bright range
    val newL = (currentL + 0.38f).coerceIn(0.72f, 0.96f)

    // Ensure high saturation/vibrancy so the color remains richly tinted rather than washed out
    if (s > 0.05f) {
        s = s.coerceAtLeast(0.80f)
    }

    val rgb = hslToRgb(h, s, newL)
    return Color(rgb[0], rgb[1], rgb[2], 1.0f)
}

