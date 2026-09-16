package com.radialtiles.presentation.components

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radialtiles.data.model.ButtonConfig
import com.radialtiles.presentation.theme.parseHexColor
import kotlin.math.*

@Composable
fun RadialPieView(
    buttons: List<ButtonConfig>,
    entityStates: Map<String, Boolean>,
    loadingEntityIds: Set<String>,
    onButtonClick: (ButtonConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    if (buttons.isEmpty()) return

    val count = buttons.size.coerceIn(1, 5)
    var pressedIndex by remember { mutableStateOf<Int?>(null) }

    val sweepAngle = 360f / count
    // Angle offset: for 4 quadrants, offset by -45 so top/bottom/left/right align nicely
    val startAngleOffset = when (count) {
        1 -> 0f
        2 -> 180f // Top & Bottom
        4 -> -135f // North, East, South, West
        else -> -90f // 12 o'clock top
    }

    val density = LocalDensity.current
    val textPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 12.sp.toPx() }
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
    }

    val iconPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 18.sp.toPx() }
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(buttons) {
                detectTapGestures(
                    onPress = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val radius = sqrt(dx * dx + dy * dy)
                        val maxRadius = min(size.width, size.height) / 2f

                        if (radius <= maxRadius) {
                            var angle = (atan2(dy, dx) * 180f / PI.toFloat() + 360f) % 360f
                            val normalized = (angle - startAngleOffset + 360f) % 360f
                            val idx = (normalized / sweepAngle).toInt() % count
                            pressedIndex = idx
                            tryAwaitRelease()
                        }
                        pressedIndex = null
                    },
                    onTap = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val radius = sqrt(dx * dx + dy * dy)
                        val maxRadius = min(size.width, size.height) / 2f

                        if (radius <= maxRadius) {
                            var angle = (atan2(dy, dx) * 180f / PI.toFloat() + 360f) % 360f
                            val normalized = (angle - startAngleOffset + 360f) % 360f
                            val idx = (normalized / sweepAngle).toInt() % count
                            if (idx in buttons.indices) {
                                onButtonClick(buttons[idx])
                            }
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = min(size.width, size.height) / 2f - 2.dp.toPx()
            val gapDp = 3.dp.toPx()

            // Draw each slice
            buttons.take(count).forEachIndexed { index, button ->
                val sliceStart = (startAngleOffset + index * sweepAngle) % 360f
                val isOn = entityStates[button.entityId] ?: false
                val isLoading = loadingEntityIds.contains(button.entityId)
                val isPressed = pressedIndex == index
                val baseColor = parseHexColor(button.colorHex)

                drawPieSlice(
                    center = center,
                    outerRadius = outerRadius,
                    startAngle = sliceStart,
                    sweepAngle = sweepAngle,
                    gap = if (count > 1) gapDp else 0f,
                    baseColor = baseColor,
                    isOn = isOn,
                    isPressed = isPressed,
                    isLoading = isLoading,
                    button = button,
                    textPaint = textPaint,
                    iconPaint = iconPaint
                )
            }
        }
    }
}

private fun DrawScope.drawPieSlice(
    center: Offset,
    outerRadius: Float,
    startAngle: Float,
    sweepAngle: Float,
    gap: Float,
    baseColor: Color,
    isOn: Boolean,
    isPressed: Boolean,
    isLoading: Boolean,
    button: ButtonConfig,
    textPaint: Paint,
    iconPaint: Paint
) {
    // Angular gap adjustment
    val angleGap = if (outerRadius > 0) (gap / outerRadius) * (180f / PI.toFloat()) else 0f
    val effectiveSweep = (sweepAngle - angleGap).coerceAtLeast(1f)
    val effectiveStart = startAngle + angleGap / 2f

    val path = Path().apply {
        moveTo(center.x, center.y)
        arcTo(
            rect = ComposeRect(
                center.x - outerRadius,
                center.y - outerRadius,
                center.x + outerRadius,
                center.y + outerRadius
            ),
            startAngleDegrees = effectiveStart,
            sweepAngleDegrees = effectiveSweep,
            forceMoveTo = false
        )
        close()
    }

    // Determine colors
    val sliceColor = when {
        isPressed -> baseColor.copy(alpha = 0.95f)
        isOn -> baseColor.copy(alpha = 0.85f)
        else -> baseColor.copy(alpha = 0.16f)
    }

    // Fill the slice
    drawPath(path = path, color = sliceColor)

    // Border stroke for crisp separation against AMOLED black background
    val borderColor = when {
        isOn -> Color.White.copy(alpha = 0.5f)
        isPressed -> Color.White.copy(alpha = 0.8f)
        else -> baseColor.copy(alpha = 0.45f)
    }
    drawPath(
        path = path,
        color = borderColor,
        style = Stroke(width = if (isOn || isPressed) 2.5f else 1.5f)
    )

    // Draw Icon and Text Label at Centroid
    val midAngleDeg = effectiveStart + effectiveSweep / 2f
    val midAngleRad = midAngleDeg * (PI.toFloat() / 180f)
    val centroidDistance = if (sweepAngle >= 350f) 0f else outerRadius * 0.58f

    val cx = center.x + centroidDistance * cos(midAngleRad)
    val cy = center.y + centroidDistance * sin(midAngleRad)

    // Icon glyph (using sleek Unicode symbols for instant crisp rendering without resource loading overhead)
    val iconGlyph = when (button.iconName.lowercase()) {
        "lightbulb", "light" -> "💡"
        "fan" -> "🌀"
        "scene" -> "✨"
        "power", "switch" -> "⏻"
        "heat", "heater" -> "♨"
        "water", "shower" -> "🚿"
        "night", "moon" -> "🌙"
        else -> when (button.domain) {
            "light" -> "💡"
            "fan" -> "🌀"
            "scene" -> "✨"
            "automation" -> "⚡"
            else -> "⏻"
        }
    }

    drawContext.canvas.nativeCanvas.apply {
        // Draw icon glyph
        iconPaint.alpha = if (isOn || isPressed) 255 else 180
        drawText(iconGlyph, cx, cy - 6f, iconPaint)

        // Draw name label
        textPaint.alpha = if (isOn || isPressed) 255 else 200
        val bounds = Rect()
        textPaint.getTextBounds(button.name, 0, button.name.length, bounds)
        drawText(button.name, cx, cy + bounds.height() + 10f, textPaint)
    }
}
