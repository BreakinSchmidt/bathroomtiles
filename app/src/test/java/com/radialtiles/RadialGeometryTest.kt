package com.radialtiles

import com.radialtiles.data.model.ButtonConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2

class RadialGeometryTest {

    private fun computeSectorIndex(x: Float, y: Float, cx: Float, cy: Float, count: Int): Int {
        val dx = x - cx
        val dy = y - cy
        val angle = (atan2(dy, dx) * 180f / PI.toFloat() + 360f) % 360f
        val sweepAngle = 360f / count
        val startAngleOffset = when (count) {
            1 -> 0f
            2 -> 180f
            4 -> -135f
            else -> -90f
        }
        val normalized = (angle - startAngleOffset + 360f) % 360f
        return (normalized / sweepAngle).toInt() % count
    }

    private fun computeGrid2x3Index(x: Float, y: Float, totalW: Float, totalH: Float): Int {
        val col = if (x < totalW / 2f) 0 else 1
        val rowHeight = totalH / 3f
        val row = (y / rowHeight).toInt().coerceIn(0, 2)
        return row * 2 + col
    }

    @Test
    fun testSingleButtonAlwaysReturnsZero() {
        assertEquals(0, computeSectorIndex(100f, 50f, 100f, 100f, 1))
        assertEquals(0, computeSectorIndex(50f, 100f, 100f, 100f, 1))
    }

    @Test
    fun testTwoButtonsSemicircleSplit() {
        // Top semicircle (y < cy) should return index 0
        assertEquals(0, computeSectorIndex(100f, 50f, 100f, 100f, 2))
        // Bottom semicircle (y > cy) should return index 1
        assertEquals(1, computeSectorIndex(100f, 150f, 100f, 100f, 2))
    }

    @Test
    fun testFourQuadrantsCardinalPoints() {
        val cx = 100f
        val cy = 100f

        // North / Top (0, -r)
        val northIdx = computeSectorIndex(100f, 20f, cx, cy, 4)
        assertEquals(0, northIdx)

        // East / Right (r, 0)
        val eastIdx = computeSectorIndex(180f, 100f, cx, cy, 4)
        assertEquals(1, eastIdx)

        // South / Bottom (0, r)
        val southIdx = computeSectorIndex(100f, 180f, cx, cy, 4)
        assertEquals(2, southIdx)

        // West / Left (-r, 0)
        val westIdx = computeSectorIndex(20f, 100f, cx, cy, 4)
        assertEquals(3, westIdx)
    }

    @Test
    fun testSixRadialSectors() {
        val cx = 100f
        val cy = 100f

        // Sector 0: 1 o'clock (Top-Right)
        assertEquals(0, computeSectorIndex(125f, 57f, cx, cy, 6))

        // Sector 1: 3 o'clock (Mid-Right)
        assertEquals(1, computeSectorIndex(150f, 100f, cx, cy, 6))

        // Sector 2: 5 o'clock (Bot-Right)
        assertEquals(2, computeSectorIndex(125f, 143f, cx, cy, 6))

        // Sector 3: 7 o'clock (Bot-Left)
        assertEquals(3, computeSectorIndex(75f, 143f, cx, cy, 6))

        // Sector 4: 9 o'clock (Mid-Left)
        assertEquals(4, computeSectorIndex(50f, 100f, cx, cy, 6))

        // Sector 5: 11 o'clock (Top-Left)
        assertEquals(5, computeSectorIndex(75f, 57f, cx, cy, 6))
    }

    @Test
    fun testGrid2x3CellIndices() {
        val w = 200f
        val h = 200f

        // Top-Left (Row 0, Col 0) -> 0
        assertEquals(0, computeGrid2x3Index(40f, 30f, w, h))
        // Top-Right (Row 0, Col 1) -> 1
        assertEquals(1, computeGrid2x3Index(160f, 30f, w, h))

        // Mid-Left (Row 1, Col 0) -> 2
        assertEquals(2, computeGrid2x3Index(40f, 100f, w, h))
        // Mid-Right (Row 1, Col 1) -> 3
        assertEquals(3, computeGrid2x3Index(160f, 100f, w, h))

        // Bot-Left (Row 2, Col 0) -> 4
        assertEquals(4, computeGrid2x3Index(40f, 170f, w, h))
        // Bot-Right (Row 2, Col 1) -> 5
        assertEquals(5, computeGrid2x3Index(160f, 170f, w, h))
    }


    @Test
    fun testDomainInference() {
        assertEquals("light", ButtonConfig.inferDomain("light.living_room"))
        assertEquals("switch", ButtonConfig.inferDomain("switch.bathroom_fan"))
        assertEquals("scene", ButtonConfig.inferDomain("scene.romantic"))
        assertEquals("automation", ButtonConfig.inferDomain("automation.motion_sensor"))
    }

    @Test
    fun testProtoLayoutArcAnglesMatchCanvasPieSlices() {
        val gapPx = 6f
        val outerRadiusPx = (454f / 2f) - 6f
        val angleGap = (gapPx / outerRadiusPx) * (180f / PI.toFloat())

        for (count in 2..6) {
            val sweepAngle = 360f / count
            val startAngleOffset = when (count) {
                2 -> 180f
                4 -> -135f
                else -> -90f
            }
            val effectiveSweep = sweepAngle - angleGap
            val firstSliceStartDeg = ((startAngleOffset + 90f + (angleGap / 2f)) % 360f + 360f) % 360f

            for (index in 0 until count) {
                val canvasStart = ((startAngleOffset + index * sweepAngle + angleGap / 2f) % 360f + 360f) % 360f
                val protoStart = (firstSliceStartDeg + index * (effectiveSweep + angleGap)) % 360f
                // ProtoLayout 0° is 12 o'clock (-90° Canvas), so protoStart == (canvasStart + 90°) % 360°
                val expectedProtoStart = (canvasStart + 90f) % 360f
                assertEquals(expectedProtoStart, protoStart, 0.01f)
            }
        }
    }

    @Test
    fun testClickTokenDeduplication() {
        val handledTokens = java.util.Collections.newSetFromMap(
            object : java.util.LinkedHashMap<String, Boolean>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>?): Boolean {
                    return size > 100
                }
            }
        )

        // First click with token A succeeds
        val tokenA = "tok_0_1"
        val isFirstClickA = synchronized(handledTokens) { handledTokens.add(tokenA) }
        org.junit.Assert.assertTrue(isFirstClickA)

        // Duplicate click with token A is suppressed
        val isDuplicateClickA = synchronized(handledTokens) { handledTokens.add(tokenA) }
        org.junit.Assert.assertFalse(isDuplicateClickA)

        // Subsequent click with fresh token B succeeds
        val tokenB = "tok_0_2"
        val isFirstClickB = synchronized(handledTokens) { handledTokens.add(tokenB) }
        org.junit.Assert.assertTrue(isFirstClickB)
    }
}
