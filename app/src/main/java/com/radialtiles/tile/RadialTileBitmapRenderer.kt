package com.radialtiles.tile

import android.graphics.*
import com.radialtiles.data.model.ButtonConfig
import com.radialtiles.data.model.DialPageConfig
import com.radialtiles.util.IconMapper
import kotlin.math.*

object RadialTileBitmapRenderer {

    fun renderDialBitmap(
        page: DialPageConfig,
        sizePx: Int = 454,
        entityStates: Map<String, Boolean> = emptyMap()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // AMOLED Black background
        canvas.drawColor(Color.BLACK)

        val buttons = page.buttons
        if (buttons.isEmpty()) return bitmap

        val count = buttons.size
        if (count >= 6) {
            renderGridBitmap(canvas, buttons.take(6), sizePx, entityStates)
        } else {
            renderPieBitmap(canvas, buttons, count, sizePx, entityStates)
        }

        return bitmap
    }

    private fun renderPieBitmap(
        canvas: Canvas,
        buttons: List<ButtonConfig>,
        count: Int,
        sizePx: Int,
        entityStates: Map<String, Boolean>
    ) {
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val outerRadius = (sizePx / 2f) - 6f
        val sweepAngle = 360f / count
        val startAngleOffset = when (count) {
            1 -> 0f
            2 -> 180f
            4 -> -135f
            else -> -90f
        }

        val gapPx = if (count > 1) 6f else 0f
        val angleGap = if (outerRadius > 0) (gapPx / outerRadius) * (180f / PI.toFloat()) else 0f
        val effectiveSweep = (sweepAngle - angleGap).coerceAtLeast(1f)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 38f
            textAlign = Paint.Align.CENTER
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val rectF = RectF(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius)

        buttons.take(count).forEachIndexed { index, button ->
            val sliceStart = (startAngleOffset + index * sweepAngle + angleGap / 2f) % 360f
            val baseColor = parseColor(button.colorHex)
            val isOn = entityStates[button.entityId] ?: false

            val alpha = if (isOn) 0xD0 else 0x40
            val fillColor = (baseColor and 0x00FFFFFF) or (alpha shl 24)
            val borderColor = if (isOn) Color.WHITE else ((baseColor and 0x00FFFFFF) or 0x90000000.toInt())

            fillPaint.color = fillColor
            strokePaint.color = borderColor

            if (count == 1) {
                canvas.drawCircle(cx, cy, outerRadius, fillPaint)
                canvas.drawCircle(cx, cy, outerRadius, strokePaint)
            } else {
                val path = Path().apply {
                    moveTo(cx, cy)
                    arcTo(rectF, sliceStart, effectiveSweep)
                    close()
                }
                canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            }

            val midAngleDeg = sliceStart + effectiveSweep / 2f
            val midAngleRad = midAngleDeg * (PI.toFloat() / 180f)
            val centroidDistance = if (count == 1) 0f else outerRadius * 0.58f

            val itemX = cx + centroidDistance * cos(midAngleRad)
            val itemY = cy + centroidDistance * sin(midAngleRad)

            val glyph = IconMapper.getGlyph(button.iconName, button.domain)
            canvas.drawText(glyph, itemX, itemY - 6f, iconPaint)

            val label = button.name.take(8)
            val bounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, bounds)
            canvas.drawText(label, itemX, itemY + bounds.height() + 14f, textPaint)
        }
    }

    private fun renderGridBitmap(
        canvas: Canvas,
        buttons: List<ButtonConfig>,
        sizePx: Int,
        entityStates: Map<String, Boolean>
    ) {
        val totalW = sizePx.toFloat()
        val totalH = sizePx.toFloat()
        val gap = 6f
        val rowHeight = (totalH - gap * 4) / 3f
        val colWidth = (totalW - gap * 3) / 2f
        val cornerRadius = 24f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        for (row in 0..2) {
            for (col in 0..1) {
                val idx = row * 2 + col
                if (idx !in buttons.indices) continue
                val button = buttons[idx]
                val isOn = entityStates[button.entityId] ?: false
                val baseColor = parseColor(button.colorHex)

                val left = gap + col * (colWidth + gap)
                val top = gap + row * (rowHeight + gap)

                val hInset = if (row == 0 || row == 2) 20f else 0f
                val actualLeft = if (col == 0) left + hInset else left
                val actualWidth = colWidth - hInset

                val alpha = if (isOn) 0xD0 else 0x38
                fillPaint.color = (baseColor and 0x00FFFFFF) or (alpha shl 24)
                strokePaint.color = if (isOn) Color.WHITE else ((baseColor and 0x00FFFFFF) or 0x80000000.toInt())

                val rect = RectF(actualLeft, top, actualLeft + actualWidth, top + rowHeight)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)

                val cellCx = actualLeft + actualWidth / 2f
                val cellCy = top + rowHeight / 2f
                val glyph = IconMapper.getGlyph(button.iconName, button.domain)
                canvas.drawText(glyph, cellCx, cellCy - 4f, iconPaint)

                val label = button.name.take(7)
                val bounds = Rect()
                textPaint.getTextBounds(label, 0, label.length, bounds)
                canvas.drawText(label, cellCx, cellCy + bounds.height() + 10f, textPaint)
            }
        }
    }

    private fun parseColor(hex: String): Int {
        return try {
            val clean = hex.removePrefix("#")
            when (clean.length) {
                6 -> Color.parseColor("#$clean")
                8 -> Color.parseColor("#$clean")
                else -> 0xFFB300.toInt()
            }
        } catch (e: Exception) {
            0xFFB300.toInt()
        }
    }
}
