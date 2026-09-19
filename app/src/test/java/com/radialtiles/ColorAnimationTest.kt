package com.radialtiles

import androidx.compose.ui.graphics.Color
import com.radialtiles.presentation.theme.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorAnimationTest {

    @Test
    fun testRgbHslRoundTrip() {
        val testColors = listOf(
            AmberAccent,
            GreenAccent,
            CyanAccent,
            CoralAccent,
            VioletAccent,
            BlueAccent,
            PinkAccent,
            OrangeAccent
        )

        for (color in testColors) {
            val hsl = rgbToHsl(color.red, color.green, color.blue)
            val rgb = hslToRgb(hsl[0], hsl[1], hsl[2])

            assertEquals("Red mismatch for $color", color.red, rgb[0], 0.02f)
            assertEquals("Green mismatch for $color", color.green, rgb[1], 0.02f)
            assertEquals("Blue mismatch for $color", color.blue, rgb[2], 0.02f)
        }
    }

    @Test
    fun testComputeBrightenedColorIncreasesLightness() {
        val testColors = listOf(
            AmberAccent,
            GreenAccent,
            CyanAccent,
            CoralAccent,
            VioletAccent,
            BlueAccent,
            PinkAccent,
            OrangeAccent,
            Color(0xFF333333) // Dark gray
        )

        for (color in testColors) {
            val originalHsl = rgbToHsl(color.red, color.green, color.blue)
            val brightColor = computeBrightenedColor(color)
            val brightHsl = rgbToHsl(brightColor.red, brightColor.green, brightColor.blue)

            // Lightness should be significantly higher
            assertTrue(
                "Brightened color lightness should be >= original for $color (orig: ${originalHsl[2]}, bright: ${brightHsl[2]})",
                brightHsl[2] >= originalHsl[2]
            )
            assertTrue(
                "Brightened lightness should be in luminous range >= 0.70f (got: ${brightHsl[2]})",
                brightHsl[2] >= 0.70f
            )

            // For non-grayscale colors, hue should remain approximately the same
            if (originalHsl[1] > 0.1f) {
                val hueDiff = abs(originalHsl[0] - brightHsl[0])
                val circularDiff = minOf(hueDiff, 360f - hueDiff)
                assertTrue(
                    "Hue should remain consistent (diff: $circularDiff for $color)",
                    circularDiff < 5.0f
                )
            }
        }
    }
}
