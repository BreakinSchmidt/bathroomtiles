package com.radialtiles.tile

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.*
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.radialtiles.MainActivity
import com.radialtiles.data.api.HomeAssistantClient
import com.radialtiles.data.model.AppConfiguration
import com.radialtiles.data.model.ButtonConfig
import com.radialtiles.data.model.DialPageConfig
import com.radialtiles.data.repository.TileConfigRepository
import androidx.compose.ui.graphics.toArgb
import com.radialtiles.feedback.HapticFeedbackManager
import com.radialtiles.presentation.theme.computeBrightenedColor
import com.radialtiles.presentation.theme.parseHexColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI

abstract class BaseRadialTileService(private val pageIndex: Int) : TileService() {

    companion object {
        private const val TAG = "BaseRadialTileService"

        // Persistent scope so in-flight Home Assistant requests survive after onTileRequest returns
        private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Counter for generating unique clickable tokens per consumed click
        private val tokenCounter = java.util.concurrent.atomic.AtomicLong(1L)

        // Bounded set of handled click tokens to prevent duplicate / ambient re-triggers
        private val handledTokens = java.util.Collections.newSetFromMap(
            object : java.util.LinkedHashMap<String, Boolean>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>?): Boolean {
                    return size > 100
                }
            }
        )
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = ResolvableFuture.create<TileBuilders.Tile>()

        serviceScope.launch {
            try {
                val repository = TileConfigRepository(applicationContext)
                val config = TileConfigRepository.cachedConfig
                    ?: repository.configFlow.first().also { TileConfigRepository.cachedConfig = it }

                val hapticManager = HapticFeedbackManager(applicationContext).apply {
                    isEnabled = config.hapticsEnabled
                }

                val haClient = HomeAssistantClient(
                    getBaseUrl = { config.haBaseUrl },
                    getLocalUrl = { config.localHaUrl },
                    getToken = { config.haToken }
                )

                // 1. Handle in-place tile button click if user tapped a sector on the watch
                val lastClickableId = requestParams.currentState?.lastClickableId
                var clickedButtonKey: String? = null
                var clickSeq: Long = 0L

                if (lastClickableId != null && lastClickableId.startsWith("toggle:")) {
                    val parts = lastClickableId.split(":")
                    if (parts.size >= 4) {
                        val entityId = parts[1]
                        val domain = parts[2]
                        val buttonId = if (parts.size >= 5) parts[3] else entityId
                        val token = if (parts.size >= 5) parts[4] else parts[3]

                        // Atomically verify and consume this click token
                        val isNewClick = synchronized(handledTokens) {
                            handledTokens.add(token)
                        }

                        if (isNewClick) {
                            val seq = tokenCounter.incrementAndGet()
                            clickedButtonKey = buttonId
                            clickSeq = seq

                            // Single vibration immediately when the button is pressed
                            hapticManager.vibratePress()

                            // Fire HA toggle asynchronously with a short WakeLock and verify server response
                            serviceScope.launch {
                                val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                                val wakeLock = runCatching {
                                    powerManager?.newWakeLock(
                                        PowerManager.PARTIAL_WAKE_LOCK,
                                        "RadialTiles::ToggleWakeLock"
                                    )?.apply {
                                        acquire(5_000L)
                                    }
                                }.getOrNull()

                                try {
                                    val confirmed = haClient.toggleEntity(entityId, domain)
                                    if (confirmed) {
                                        // Quick double vibration once the server confirms the action
                                        hapticManager.vibrateServerConfirm()
                                    } else {
                                        Log.w(TAG, "Server did not confirm toggle for $entityId ($domain)")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed HA toggle for $entityId", e)
                                } finally {
                                    runCatching {
                                        if (wakeLock?.isHeld == true) {
                                            wakeLock.release()
                                        }
                                    }
                                }
                            }
                        } else {
                            Log.d(TAG, "Suppressing duplicate click with token: $token")
                        }
                    }
                }

                // 2. Resolve the page for this tile
                val pages = config.pages.ifEmpty { AppConfiguration.defaultPages() }
                val page = pages.getOrElse(pageIndex) {
                    pages.firstOrNull() ?: DialPageConfig("p$pageIndex", "Tile ${pageIndex + 1}", emptyList())
                }

                // Pre-warm the cache in background so subsequent renders are 0ms
                if (page.buttons.isNotEmpty()) {
                    CoroutineScope(Dispatchers.Default).launch {
                        RadialTileBitmapRenderer.prewarmCache(page, 454)
                    }
                }

                val buttonsKey = page.buttons.joinToString("|") { "${it.id}_${it.name}_${it.colorHex}_${it.iconName}" }
                val baseVersion = "${page.id}_${buttonsKey.hashCode()}"
                val nextToken = "tok_${pageIndex}_${tokenCounter.incrementAndGet()}"

                // 3. Build ProtoLayout with interactive radial dial and radial section highlight
                val rootLayout = buildRadialTileLayout(
                    page = page,
                    requestParams = requestParams,
                    token = nextToken,
                    clickedButtonKey = clickedButtonKey,
                    clickSeq = clickSeq
                )

                val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(rootLayout)
                            .build()
                    )
                    .build()

                val tile = TileBuilders.Tile.Builder()
                    .setResourcesVersion(baseVersion)
                    .setTileTimeline(
                        TimelineBuilders.Timeline.Builder()
                            .addTimelineEntry(timelineEntry)
                            .build()
                    )
                    .build()

                future.set(tile)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating tile", e)
                val fallbackLayout = PrimaryLayout.Builder(requestParams.deviceConfiguration)
                    .setContent(
                        Text.Builder(applicationContext, "RadialTiles")
                            .setTypography(Typography.TYPOGRAPHY_TITLE3)
                            .setColor(ColorBuilders.argb(0xFFFFB300.toInt()))
                            .build()
                    )
                    .build()

                val tile = TileBuilders.Tile.Builder()
                    .setResourcesVersion("fallback")
                    .setTileTimeline(
                        TimelineBuilders.Timeline.Builder()
                            .addTimelineEntry(
                                TimelineBuilders.TimelineEntry.Builder()
                                    .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(fallbackLayout).build())
                                    .build()
                            )
                            .build()
                    )
                    .build()
                future.set(tile)
            }
        }

        return future
    }

    private fun buildRadialTileLayout(
        page: DialPageConfig,
        requestParams: RequestBuilders.TileRequest,
        token: String,
        clickedButtonKey: String?,
        clickSeq: Long
    ): LayoutElementBuilders.LayoutElement {
        val rootBox = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())

        if (page.buttons.isEmpty()) {
            val launchAppAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(MainActivity::class.java.name)
                        .build()
                )
                .build()

            val emptyClickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(launchAppAction)
                .setId("open_app_empty")
                .build()

            val text = Text.Builder(this, "Tap to configure in app")
                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                .setColor(ColorBuilders.argb(0xFFF59E0B.toInt()))
                .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(emptyClickable).build())
                .build()

            return LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .addContent(text)
                .build()
        }

        // 1. Dial image background (Rendered radial dial)
        val dialImage = LayoutElementBuilders.Image.Builder()
            .setResourceId("dial_image_$pageIndex")
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .build()
        rootBox.addContent(dialImage)

        // 2. Clickable touch sectors overlay matching radial geometry
        val screenDp = requestParams.deviceConfiguration.screenWidthDp
            .takeIf { it > 0 }?.toFloat() ?: 227f
        val overlay = buildClickableTouchOverlay(
            buttons = page.buttons,
            token = token,
            screenDp = screenDp,
            clickedButtonKey = clickedButtonKey,
            clickSeq = clickSeq
        )
        rootBox.addContent(overlay)

        return rootBox.build()
    }

    private fun buildClickableTouchOverlay(
        buttons: List<ButtonConfig>,
        token: String,
        screenDp: Float,
        clickedButtonKey: String?,
        clickSeq: Long
    ): LayoutElementBuilders.LayoutElement {
        val count = buttons.size
        if (count == 0) return LayoutElementBuilders.Box.Builder().build()

        val outerMarginDp = screenDp * (6f / 454f)
        val pieRadiusDp = ((screenDp / 2f) - outerMarginDp).coerceAtLeast(10f)

        return when {
            count == 1 -> {
                val btn = buttons[0]
                val isClicked = isButtonClicked(btn, clickedButtonKey)
                val corner = ModifiersBuilders.Corner.Builder()
                    .setRadius(DimensionBuilders.dp(pieRadiusDp))
                    .build()
                val background = ModifiersBuilders.Background.Builder()
                    .setCorner(corner)
                    .setColor(buildHighlightColorProp(btn, isClicked, clickSeq))
                    .build()
                val modifiers = ModifiersBuilders.Modifiers.Builder()
                    .setClickable(buildButtonClickable(btn, token))
                    .setBackground(background)
                    .build()

                LayoutElementBuilders.Box.Builder()
                    .setWidth(DimensionBuilders.dp(pieRadiusDp * 2f))
                    .setHeight(DimensionBuilders.dp(pieRadiusDp * 2f))
                    .setModifiers(modifiers)
                    .build()
            }
            count in 2..6 -> {
                buildRadialArcTouchOverlay(
                    buttons = buttons,
                    token = token,
                    screenDp = screenDp,
                    pieRadiusDp = pieRadiusDp,
                    clickedButtonKey = clickedButtonKey,
                    clickSeq = clickSeq
                )
            }
            else -> {
                // > 6 buttons: 2 columns x 3 rows rounded-cell grid
                val col = LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())

                val chunked = buttons.take(6).chunked(2)
                chunked.forEach { rowButtons ->
                    val row = LayoutElementBuilders.Row.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHeight(DimensionBuilders.weight(1f))
                    rowButtons.forEach { b ->
                        val isClicked = isButtonClicked(b, clickedButtonKey)
                        row.addContent(
                            buildGridTouchSector(
                                btn = b,
                                token = token,
                                isClicked = isClicked,
                                clickSeq = clickSeq,
                                widthWeight = 1f
                            )
                        )
                    }
                    col.addContent(row.build())
                }
                col.build()
            }
        }
    }

    /**
     * Builds a radial pie-slice overlay using ProtoLayout Arc + butt-capped ArcLine wedges.
     * Each ArcLine extends radially from the center to pieRadiusDp and sweeps the exact angle
     * of its corresponding slice in RadialTileBitmapRenderer.renderPieBitmap, so both hit-testing
     * and the tap highlight match the radial section rather than a rectangle.
     */
    private fun buildRadialArcTouchOverlay(
        buttons: List<ButtonConfig>,
        token: String,
        screenDp: Float,
        pieRadiusDp: Float,
        clickedButtonKey: String?,
        clickSeq: Long
    ): LayoutElementBuilders.LayoutElement {
        val count = buttons.size
        val sweepAngle = 360f / count
        val startAngleOffset = when (count) {
            1 -> 0f
            2 -> 180f
            4 -> -135f
            else -> -90f
        }
        val gapPx = 6f
        val outerRadiusPx = (454f / 2f) - 6f
        val angleGap = (gapPx / outerRadiusPx) * (180f / PI.toFloat())
        val effectiveSweep = (sweepAngle - angleGap).coerceAtLeast(1f)

        // Convert Canvas angle (0° = 3 o'clock) to ProtoLayout Arc angle (0° = 12 o'clock)
        val firstSliceStartDeg = ((startAngleOffset + 90f + (angleGap / 2f)) % 360f + 360f) % 360f

        val arcBuilder = LayoutElementBuilders.Arc.Builder()
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .setAnchorAngle(DimensionBuilders.degrees(firstSliceStartDeg))

        buttons.forEachIndexed { index, btn ->
            val isClicked = isButtonClicked(btn, clickedButtonKey)
            val sliceModifiers = ModifiersBuilders.ArcModifiers.Builder()
                .setClickable(buildButtonClickable(btn, token))
                .build()

            val sliceArcLine = LayoutElementBuilders.ArcLine.Builder()
                .setLength(DimensionBuilders.degrees(effectiveSweep))
                .setThickness(DimensionBuilders.dp(pieRadiusDp))
                .setStrokeCap(LayoutElementBuilders.STROKE_CAP_BUTT)
                .setColor(buildHighlightColorProp(btn, isClicked, clickSeq))
                .setModifiers(sliceModifiers)
                .build()

            arcBuilder.addContent(sliceArcLine)

            if (index < count - 1) {
                val spacer = LayoutElementBuilders.ArcSpacer.Builder()
                    .setLength(DimensionBuilders.degrees(angleGap))
                    .setThickness(DimensionBuilders.dp(1f))
                    .build()
                arcBuilder.addContent(spacer)
            }
        }

        // Small center cap matching innerRadius (12px at 454px) to keep the center hub pure AMOLED black
        val innerRadiusDp = (screenDp * (12f / 454f)).coerceAtLeast(3f)
        val centerCapModifiers = ModifiersBuilders.Modifiers.Builder()
            .setBackground(
                ModifiersBuilders.Background.Builder()
                    .setColor(ColorBuilders.argb(0xFF000000.toInt()))
                    .setCorner(
                        ModifiersBuilders.Corner.Builder()
                            .setRadius(DimensionBuilders.dp(innerRadiusDp))
                            .build()
                    )
                    .build()
            )
            .build()

        val centerCap = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(innerRadiusDp * 2f))
            .setHeight(DimensionBuilders.dp(innerRadiusDp * 2f))
            .setModifiers(centerCapModifiers)
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(pieRadiusDp * 2f))
            .setHeight(DimensionBuilders.dp(pieRadiusDp * 2f))
            .addContent(arcBuilder.build())
            .addContent(centerCap)
            .build()
    }

    private fun isButtonClicked(btn: ButtonConfig, clickedButtonKey: String?): Boolean {
        if (clickedButtonKey.isNullOrBlank()) return false
        val key = btn.id.ifBlank { btn.entityId }
        return key == clickedButtonKey || btn.entityId == clickedButtonKey
    }

    private fun buildButtonClickable(
        btn: ButtonConfig,
        token: String
    ): ModifiersBuilders.Clickable {
        val buttonKey = btn.id.ifBlank { btn.entityId }.replace(":", "_")
        val clickAction = ActionBuilders.LoadAction.Builder().build()
        return ModifiersBuilders.Clickable.Builder()
            .setOnClick(clickAction)
            .setId("toggle:${btn.entityId}:${btn.domain}:$buttonKey:$token")
            // Native visual feedback ensures instantaneous, zero-latency response on tap
            .setVisualFeedbackEnabled(true)
            .build()
    }

    /**
     * Returns a transparent color for idle slices, or a self-decaying client-side animated highlight
     * (fading from brightened button color to transparent in 450ms) for the just-pressed slice.
     */
    private fun buildHighlightColorProp(
        btn: ButtonConfig,
        isClicked: Boolean,
        clickSeq: Long
    ): ColorBuilders.ColorProp {
        if (!isClicked) {
            return ColorBuilders.argb(0x00000000)
        }

        val brightenedRgb = computeBrightenedColor(parseHexColor(btn.colorHex)).toArgb() and 0x00FFFFFF
        val startArgb = (0x99 shl 24) or brightenedRgb
        // Alpha is 0x00 (fully transparent) at the end of the animation; low bits encode clickSeq
        // so consecutive clicks on the same slice produce a fresh dynamic expression fingerprint.
        val endArgb = (clickSeq.toInt() and 0x00FFFFFF)

        val animSpec = AnimationParameterBuilders.AnimationSpec.Builder()
            .setAnimationParameters(
                AnimationParameterBuilders.AnimationParameters.Builder()
                    .setDurationMillis(450L)
                    .setEasing(AnimationParameterBuilders.Easing.cubicBezier(0.2f, 0.0f, 0.0f, 1.0f))
                    .build()
            )
            .build()

        return ColorBuilders.ColorProp.Builder(0x00000000)
            .setDynamicValue(
                DynamicBuilders.DynamicColor.animate(startArgb, endArgb, animSpec)
            )
            .build()
    }

    private fun buildGridTouchSector(
        btn: ButtonConfig,
        token: String,
        isClicked: Boolean,
        clickSeq: Long,
        widthWeight: Float? = null,
        heightWeight: Float? = null
    ): LayoutElementBuilders.LayoutElement {
        val corner = ModifiersBuilders.Corner.Builder()
            .setRadius(DimensionBuilders.dp(18f))
            .build()
        val background = ModifiersBuilders.Background.Builder()
            .setCorner(corner)
            .setColor(buildHighlightColorProp(btn, isClicked, clickSeq))
            .build()

        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(buildButtonClickable(btn, token))
            .setBackground(background)
            .setPadding(
                ModifiersBuilders.Padding.Builder()
                    .setAll(DimensionBuilders.dp(4f))
                    .build()
            )
            .build()

        val box = LayoutElementBuilders.Box.Builder()
            .setModifiers(modifiers)

        if (widthWeight != null) {
            box.setWidth(DimensionBuilders.weight(widthWeight))
        } else {
            box.setWidth(DimensionBuilders.expand())
        }

        if (heightWeight != null) {
            box.setHeight(DimensionBuilders.weight(heightWeight))
        } else {
            box.setHeight(DimensionBuilders.expand())
        }

        return box.build()
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val future = ResolvableFuture.create<ResourceBuilders.Resources>()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = TileConfigRepository(applicationContext)
                val config = TileConfigRepository.cachedConfig
                    ?: repository.configFlow.first().also { TileConfigRepository.cachedConfig = it }
                val pages = config.pages.ifEmpty { AppConfiguration.defaultPages() }
                val page = pages.getOrElse(pageIndex) {
                    pages.firstOrNull() ?: DialPageConfig("p$pageIndex", "Tile ${pageIndex + 1}", emptyList())
                }

                val normalBytes = RadialTileBitmapRenderer.getNormalDialBytes(page, 454)
                val resources = ResourceBuilders.Resources.Builder()
                    .setVersion(requestParams.version)
                    .addIdToImageMapping(
                        "dial_image_$pageIndex",
                        createInlineImageResource(normalBytes)
                    )
                    .build()

                future.set(resources)
            } catch (e: Exception) {
                Log.e(TAG, "Error providing tile resources", e)
                val resources = ResourceBuilders.Resources.Builder()
                    .setVersion(requestParams.version)
                    .build()
                future.set(resources)
            }
        }

        return future
    }

    private fun createInlineImageResource(bytes: ByteArray): ResourceBuilders.ImageResource {
        val inlineImage = ResourceBuilders.InlineImageResource.Builder()
            .setData(bytes)
            .setWidthPx(454)
            .setHeightPx(454)
            .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
            .build()

        return ResourceBuilders.ImageResource.Builder()
            .setInlineResource(inlineImage)
            .build()
    }
}
