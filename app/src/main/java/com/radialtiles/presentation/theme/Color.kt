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
