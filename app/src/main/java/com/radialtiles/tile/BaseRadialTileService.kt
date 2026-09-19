package com.radialtiles.tile

import android.util.Log
import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.*
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
import com.radialtiles.feedback.HapticFeedbackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

abstract class BaseRadialTileService(private val pageIndex: Int) : TileService() {

    companion object {
        private const val TAG = "BaseRadialTileService"

        // Counter for generating unique clickable tokens per tile build
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

        CoroutineScope(Dispatchers.IO).launch {
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

                if (lastClickableId != null && lastClickableId.startsWith("toggle:")) {
                    val parts = lastClickableId.split(":")
                    if (parts.size >= 4) {
                        val entityId = parts[1]
                        val domain = parts[2]
                        val token = parts[3]

                        // Atomically verify and consume this click token
                        val isNewClick = synchronized(handledTokens) {
                            handledTokens.add(token)
                        }

                        if (isNewClick) {
                            val isSceneOrAutomation = domain in listOf("scene", "automation", "script")
                            if (isSceneOrAutomation) {
                                hapticManager.vibrateScene()
                            } else {
                                hapticManager.vibrateToggleOn()
                            }

                            // Fire HA toggle asynchronously in background
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    haClient.toggleEntity(entityId, domain)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed HA toggle for $entityId", e)
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
                val currentToken = "tok_${pageIndex}_${tokenCounter.incrementAndGet()}"
                val resourceVersion = "${baseVersion}__TS__${currentToken}"

                // 3. Build ProtoLayout with interactive radial dial
                val rootLayout = buildRadialTileLayout(page, requestParams, currentToken)

                val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(rootLayout)
                            .build()
                    )
                    .build()

                val tile = TileBuilders.Tile.Builder()
                    .setResourcesVersion(resourceVersion)
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
        token: String
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

        // 2. Clickable touch sectors overlay
        val overlay = buildClickableTouchOverlay(page.buttons, token)
        rootBox.addContent(overlay)

        return rootBox.build()
    }

    private fun buildClickableTouchOverlay(buttons: List<ButtonConfig>, token: String): LayoutElementBuilders.LayoutElement {
        val count = buttons.size
        if (count == 0) return LayoutElementBuilders.Box.Builder().build()

        return when (count) {
            1 -> {
                buildTouchSector(buttons[0], token)
            }
            2 -> {
                // Top half -> button 0, Bottom half -> button 1
                LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .addContent(buildTouchSector(buttons[0], token, heightWeight = 1f))
                    .addContent(buildTouchSector(buttons[1], token, heightWeight = 1f))
                    .build()
            }
            3 -> {
                // Slices: 0 is Top-Right, 1 is Bottom, 2 is Top-Left
                val topRow = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.weight(1f))
                    .addContent(buildTouchSector(buttons[2], token, widthWeight = 1f))
                    .addContent(buildTouchSector(buttons[0], token, widthWeight = 1f))
                    .build()

                LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .addContent(topRow)
                    .addContent(buildTouchSector(buttons[1], token, heightWeight = 1f))
                    .build()
            }
            4 -> {
                // Slices: 0 is North, 1 is East, 2 is South, 3 is West
                val topRow = buildTouchSector(buttons[0], token, heightWeight = 1f)
                val midRow = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.weight(1f))
                    .addContent(buildTouchSector(buttons[3], token, widthWeight = 1f))
                    .addContent(buildTouchSector(buttons[1], token, widthWeight = 1f))
                    .build()
                val botRow = buildTouchSector(buttons[2], token, heightWeight = 1f)

                LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .addContent(topRow)
                    .addContent(midRow)
                    .addContent(botRow)
                    .build()
            }
            5 -> {
                // Slices: 0 (Top-Right), 1 (Mid-Right), 2 (Bottom), 3 (Mid-Left), 4 (Top-Left)
                val topRow = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.weight(1f))
                    .addContent(buildTouchSector(buttons[4], token, widthWeight = 1f))
                    .addContent(buildTouchSector(buttons[0], token, widthWeight = 1f))
                    .build()

                val midRow = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.weight(1f))
                    .addContent(buildTouchSector(buttons[3], token, widthWeight = 1f))
                    .addContent(buildTouchSector(buttons[1], token, widthWeight = 1f))
                    .build()

                val botRow = buildTouchSector(buttons[2], token, heightWeight = 1f)

                LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .addContent(topRow)
                    .addContent(midRow)
                    .addContent(botRow)
                    .build()
            }
            6 -> {
                // 6 Radial buttons layout:
                // Slices rotate clockwise starting at 12 o'clock (-90°):
                // 0: Top-Right (12-2 o'clock), 1: Mid-Right (2-4 o'clock), 2: Bot-Right (4-6 o'clock)
                // 3: Bot-Left (6-8 o'clock),   4: Mid-Left (8-10 o'clock), 5: Top-Left (10-12 o'clock)
                val topRow = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.weight(1f))
                    .addContent(buildTouchSector(buttons[5], token, widthWeight = 1f))
                    .addContent(buildTouchSector(buttons[0], token, widthWeight = 1f))
                    .build()

                val midRow = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.weight(1.1f))
                    .addContent(buildTouchSector(buttons[4], token, widthWeight = 1.1f))
                    .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.weight(0.8f)).build())
                    .addContent(buildTouchSector(buttons[1], token, widthWeight = 1.1f))
                    .build()

                val botRow = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.weight(1f))
                    .addContent(buildTouchSector(buttons[3], token, widthWeight = 1f))
                    .addContent(buildTouchSector(buttons[2], token, widthWeight = 1f))
                    .build()

                LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .addContent(topRow)
                    .addContent(midRow)
                    .addContent(botRow)
                    .build()
            }
            else -> {
                // Fallback for > 6 buttons: 2 columns x rows grid
                val col = LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())

                val chunked = buttons.take(6).chunked(2)
                chunked.forEach { rowButtons ->
                    val row = LayoutElementBuilders.Row.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHeight(DimensionBuilders.weight(1f))
                    rowButtons.forEach { b ->
                        row.addContent(buildTouchSector(b, token, widthWeight = 1f))
                    }
                    col.addContent(row.build())
                }
                col.build()
            }
        }
    }

    private fun buildTouchSector(
        btn: ButtonConfig,
        token: String,
        widthWeight: Float? = null,
        heightWeight: Float? = null
    ): LayoutElementBuilders.LayoutElement {
        val clickAction = ActionBuilders.LoadAction.Builder().build()
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(clickAction)
            .setId("toggle:${btn.entityId}:${btn.domain}:$token")
            .setVisualFeedbackEnabled(true)
            .build()

        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(clickable)
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
