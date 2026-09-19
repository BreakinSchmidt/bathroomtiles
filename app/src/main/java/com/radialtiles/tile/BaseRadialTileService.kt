package com.radialtiles.tile

import android.graphics.Bitmap
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
import java.io.ByteArrayOutputStream

abstract class BaseRadialTileService(private val pageIndex: Int) : TileService() {

    companion object {
        private const val TAG = "BaseRadialTileService"
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = ResolvableFuture.create<TileBuilders.Tile>()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = TileConfigRepository(applicationContext)
                val config = repository.configFlow.first()
                val hapticManager = HapticFeedbackManager(applicationContext)
                hapticManager.isEnabled = config.hapticsEnabled

                val haClient = HomeAssistantClient(
                    getBaseUrl = { config.haBaseUrl },
                    getLocalUrl = { config.localHaUrl },
                    getToken = { config.haToken }
                )

                // 1. Handle in-place tile button click if user tapped a sector on the home screen
                val lastClickableId = requestParams.currentState?.lastClickableId
                if (lastClickableId != null && lastClickableId.startsWith("toggle:")) {
                    val parts = lastClickableId.split(":")
                    if (parts.size >= 3) {
                        val entityId = parts[1]
                        val domain = parts[2]
                        haClient.toggleEntity(entityId, domain)
                        hapticManager.vibrateToggleOn()
                    }
                }

                // 2. Resolve the page for this tile
                val pages = config.pages.ifEmpty { AppConfiguration.defaultPages() }
                val page = pages.getOrElse(pageIndex) {
                    pages.firstOrNull() ?: DialPageConfig("p$pageIndex", "Tile ${pageIndex + 1}", emptyList())
                }

                // Dynamic resource version based on button config to bust ProtoLayout resource cache on changes
                val buttonsKey = page.buttons.joinToString("|") { "${it.id}_${it.name}_${it.colorHex}_${it.iconName}" }
                val resourceVersion = "${page.id}_${buttonsKey.hashCode()}"

                // 3. Build ProtoLayout with interactive radial dial
                val rootLayout = buildRadialTileLayout(page, requestParams)

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
        requestParams: RequestBuilders.TileRequest
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

        // 1. Dial image background (Rendered radial dial matching in-app Canvas)
        val dialImage = LayoutElementBuilders.Image.Builder()
            .setResourceId("dial_image_$pageIndex")
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .build()
        rootBox.addContent(dialImage)

        // 2. Clickable touch sectors overlay
        val overlay = buildClickableTouchOverlay(page.buttons)
        rootBox.addContent(overlay)

        // 3. Tile title at top center (tappable to open app)
        val launchAppAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            )
            .build()

        val titleClickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(launchAppAction)
            .setId("open_app_page_$pageIndex")
            .build()

        val titleText = Text.Builder(this, page.title.uppercase())
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(ColorBuilders.argb(0xFFF59E0B.toInt()))
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(titleClickable).build())
            .build()

        val titleColumn = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8f)).build())
            .addContent(titleText)
            .build()

        rootBox.addContent(titleColumn)

        return rootBox.build()
    }

    private fun buildClickableTouchOverlay(buttons: List<ButtonConfig>): LayoutElementBuilders.LayoutElement {
        val count = buttons.size
        if (count == 0) return LayoutElementBuilders.Box.Builder().build()

        return when (count) {
            1 -> {
                buildTouchSector(buttons[0])
            }
            2 -> {
                // Top half -> button 0, Bottom half -> button 1
                LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .addContent(buildTouchSector(buttons[0], heightWeight = 1f))
                    .addContent(buildTouchSector(buttons[1], heightWeight = 1f))
                    .build()
            }
            3 -> {
                // Top row: button 0 (left), button 1 (right); Bottom half: button 2
                val topRow = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.weight(1f))
                    .addContent(buildTouchSector(buttons[0], widthWeight = 1f))
                    .addContent(buildTouchSector(buttons[1], widthWeight = 1f))
                    .build()

                LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .addContent(topRow)
                    .addContent(buildTouchSector(buttons[2], heightWeight = 1f))
                    .build()
            }
            4 -> {
                // 4 Quadrants: Top-left, Top-right, Bottom-left, Bottom-right
                val topRow = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.weight(1f))
                    .addContent(buildTouchSector(buttons[0], widthWeight = 1f))
                    .addContent(buildTouchSector(buttons[1], widthWeight = 1f))
                    .build()

                val bottomRow = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.weight(1f))
                    .addContent(buildTouchSector(buttons[2], widthWeight = 1f))
                    .addContent(buildTouchSector(buttons[3], widthWeight = 1f))
                    .build()

                LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .addContent(topRow)
                    .addContent(bottomRow)
                    .build()
            }
            5 -> {
                // Top row: 0, 1; Middle: 2; Bottom row: 3, 4
                val topRow = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.weight(1f))
                    .addContent(buildTouchSector(buttons[0], widthWeight = 1f))
                    .addContent(buildTouchSector(buttons[1], widthWeight = 1f))
                    .build()

                val midRow = buildTouchSector(buttons[2], heightWeight = 1f)

                val bottomRow = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.weight(1f))
                    .addContent(buildTouchSector(buttons[3], widthWeight = 1f))
                    .addContent(buildTouchSector(buttons[4], widthWeight = 1f))
                    .build()

                LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .addContent(topRow)
                    .addContent(midRow)
                    .addContent(bottomRow)
                    .build()
            }
            else -> {
                // 6 Buttons Grid: 2 columns x 3 rows
                val col = LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())

                val chunked = buttons.take(6).chunked(2)
                chunked.forEach { rowButtons ->
                    val row = LayoutElementBuilders.Row.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHeight(DimensionBuilders.weight(1f))
                    rowButtons.forEach { b ->
                        row.addContent(buildTouchSector(b, widthWeight = 1f))
                    }
                    col.addContent(row.build())
                }
                col.build()
            }
        }
    }

    private fun buildTouchSector(
        btn: ButtonConfig,
        widthWeight: Float? = null,
        heightWeight: Float? = null
    ): LayoutElementBuilders.LayoutElement {
        val clickAction = ActionBuilders.LoadAction.Builder().build()
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(clickAction)
            .setId("toggle:${btn.entityId}:${btn.domain}")
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
                val config = repository.configFlow.first()
                val pages = config.pages.ifEmpty { AppConfiguration.defaultPages() }
                val page = pages.getOrElse(pageIndex) {
                    pages.firstOrNull() ?: DialPageConfig("p$pageIndex", "Tile ${pageIndex + 1}", emptyList())
                }

                // Render dynamic dial bitmap matching in-app Canvas
                val bitmap = RadialTileBitmapRenderer.renderDialBitmap(page, sizePx = 454)
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val bytes = stream.toByteArray()

                val inlineImage = ResourceBuilders.InlineImageResource.Builder()
                    .setData(bytes)
                    .setWidthPx(454)
                    .setHeightPx(454)
                    .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                    .build()

                val imageResource = ResourceBuilders.ImageResource.Builder()
                    .setInlineResource(inlineImage)
                    .build()

                val resources = ResourceBuilders.Resources.Builder()
                    .setVersion(requestParams.version)
                    .addIdToImageMapping("dial_image_$pageIndex", imageResource)
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
}
