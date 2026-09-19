package com.radialtiles.tile

import android.graphics.*
import com.radialtiles.data.model.ButtonConfig
import com.radialtiles.data.model.DialPageConfig
import com.radialtiles.util.IconMapper
import kotlin.math.*

object RadialTileBitmapRenderer {

    private val dialBytesCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    fun clearCache() {
        dialBytesCache.clear()
    }

    fun getNormalDialBytes(page: DialPageConfig, sizePx: Int = 454): ByteArray {
        val buttonsKey = page.buttons.joinToString("|") { "${it.id}_${it.colorHex}_${it.iconName}_${it.name}" }
        val key = "${page.id}_${sizePx}_${buttonsKey}"
        return dialBytesCache.computeIfAbsent(key) {
            val bitmap = renderDialBitmap(page, sizePx)
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }

    fun prewarmCache(page: DialPageConfig, sizePx: Int = 454) {
        getNormalDialBytes(page, sizePx)
    }

    fun renderDialBitmap(
        page: DialPageConfig,
        sizePx: Int = 454
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // AMOLED Black background
        canvas.drawColor(Color.BLACK)

        val buttons = page.buttons
        if (buttons.isEmpty()) return bitmap

        val count = buttons.size
        if (count > 6) {
            renderGridBitmap(canvas, buttons.take(6), sizePx)
        } else {
            renderPieBitmap(canvas, buttons.take(6), count.coerceIn(1, 6), sizePx)
        }

        return bitmap
    }

    private fun renderPieBitmap(
        canvas: Canvas,
        buttons: List<ButtonConfig>,
        count: Int,
        sizePx: Int
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
            strokeWidth = 3.0f
        }

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (count >= 6) 32f else 38f
            textAlign = Paint.Align.CENTER
            alpha = 200
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (count >= 6) 18f else 22f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            alpha = 200
        }

        val rectF = RectF(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius)

        buttons.take(count).forEachIndexed { index, button ->
            val sliceStart = (startAngleOffset + index * sweepAngle + angleGap / 2f) % 360f
            val baseColor = parseColor(button.colorHex)

            val alpha = 0x48
            val fillColor = (baseColor and 0x00FFFFFF) or (alpha shl 24)
            val borderColor = ((baseColor and 0x00FFFFFF) or 0x80000000.toInt())

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

            val label = button.name.take(if (count >= 6) 7 else 8)
            val bounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, bounds)
            canvas.drawText(label, itemX, itemY + bounds.height() + 14f, textPaint)
        }
    }

    private fun renderGridBitmap(
        canvas: Canvas,
        buttons: List<ButtonConfig>,
        sizePx: Int
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
            alpha = 200
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            alpha = 200
        }

        for (row in 0..2) {
            for (col in 0..1) {
                val idx = row * 2 + col
                if (idx !in buttons.indices) continue
                val button = buttons[idx]
                val baseColor = parseColor(button.colorHex)

                val left = gap + col * (colWidth + gap)
                val top = gap + row * (rowHeight + gap)

                val hInset = if (row == 0 || row == 2) 20f else 0f
                val actualLeft = if (col == 0) left + hInset else left
                val actualWidth = colWidth - hInset

                val alpha = 0x48
                val fillColor = (baseColor and 0x00FFFFFF) or (alpha shl 24)
                val borderColor = ((baseColor and 0x00FFFFFF) or 0x80000000.toInt())

                fillPaint.color = fillColor
                strokePaint.color = borderColor

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

