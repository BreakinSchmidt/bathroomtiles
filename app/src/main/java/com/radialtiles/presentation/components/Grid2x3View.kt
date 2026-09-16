package com.radialtiles.presentation.components

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radialtiles.data.model.ButtonConfig
import com.radialtiles.presentation.theme.parseHexColor
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun Grid2x3View(
    buttons: List<ButtonConfig>,
    entityStates: Map<String, Boolean>,
    loadingEntityIds: Set<String>,
    onButtonClick: (ButtonConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    if (buttons.size < 6) return

    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current

    val textPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 11.sp.toPx() }
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
    }

    val iconPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 15.sp.toPx() }
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
                        val col = if (offset.x < size.width / 2f) 0 else 1
                        val rowHeight = size.height / 3f
                        val row = (offset.y / rowHeight).toInt().coerceIn(0, 2)
                        val idx = row * 2 + col

                        // Check within watch circle
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        if (sqrt(dx * dx + dy * dy) <= min(cx, cy)) {
                            pressedIndex = idx
                            tryAwaitRelease()
                        }
                        pressedIndex = null
                    },
                    onTap = { offset ->
                        val col = if (offset.x < size.width / 2f) 0 else 1
                        val rowHeight = size.height / 3f
                        val row = (offset.y / rowHeight).toInt().coerceIn(0, 2)
                        val idx = row * 2 + col

                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        if (sqrt(dx * dx + dy * dy) <= min(cx, cy) && idx in buttons.indices) {
                            onButtonClick(buttons[idx])
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalW = size.width
            val totalH = size.height
            val gap = 3.dp.toPx()
            val rowHeight = (totalH - gap * 4) / 3f
            val colWidth = (totalW - gap * 3) / 2f
            val cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())

            for (row in 0..2) {
                for (col in 0..1) {
                    val idx = row * 2 + col
                    if (idx !in buttons.indices) continue
                    val button = buttons[idx]
                    val isOn = entityStates[button.entityId] ?: false
                    val isPressed = pressedIndex == idx
                    val baseColor = parseHexColor(button.colorHex)

                    // Compute cell bounding box
                    val left = gap + col * (colWidth + gap)
                    val top = gap + row * (rowHeight + gap)

                    // Indent top and bottom rows slightly to conform to circular bezel
                    val hInset = if (row == 0 || row == 2) 10.dp.toPx() else 0f
                    val actualLeft = if (col == 0) left + hInset else left
                    val actualWidth = colWidth - hInset

                    val cellColor = when {
                        isPressed -> baseColor.copy(alpha = 0.95f)
                        isOn -> baseColor.copy(alpha = 0.85f)
                        else -> baseColor.copy(alpha = 0.18f)
                    }

                    val borderColor = when {
                        isOn -> Color.White.copy(alpha = 0.5f)
                        isPressed -> Color.White.copy(alpha = 0.8f)
                        else -> baseColor.copy(alpha = 0.45f)
                    }

                    // Draw rounded cell background
                    drawRoundRect(
                        color = cellColor,
                        topLeft = Offset(actualLeft, top),
                        size = Size(actualWidth, rowHeight),
                        cornerRadius = cornerRadius
                    )

                    // Draw border
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(actualLeft, top),
                        size = Size(actualWidth, rowHeight),
                        cornerRadius = cornerRadius,
                        style = Stroke(width = if (isOn || isPressed) 2f else 1.2f)
                    )

                    // Draw Icon & Label
                    val cellCx = actualLeft + actualWidth / 2f
                    val cellCy = top + rowHeight / 2f

                    val iconGlyph = when (button.iconName.lowercase()) {
                        "lightbulb", "light" -> "💡"
                        "fan" -> "🌀"
                        "scene" -> "✨"
                        "power", "switch" -> "⏻"
                        "heat", "heater" -> "♨"
                        "water", "shower" -> "🚿"
                        "night", "moon" -> "🌙"
                        else -> "⏻"
                    }

                    drawContext.canvas.nativeCanvas.apply {
                        iconPaint.alpha = if (isOn || isPressed) 255 else 180
                        drawText(iconGlyph, cellCx, cellCy - 4f, iconPaint)

                        textPaint.alpha = if (isOn || isPressed) 255 else 200
                        val bounds = Rect()
                        textPaint.getTextBounds(button.name, 0, button.name.length, bounds)
                        drawText(button.name, cellCx, cellCy + bounds.height() + 8f, textPaint)
                    }
                }
            }
        }
    }
}
