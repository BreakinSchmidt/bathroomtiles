package com.radialtiles.tile

import android.content.Intent
import android.graphics.Color as AndroidColor
import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.*
import androidx.wear.protolayout.material.CompactChip
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
        private const val RESOURCES_VERSION = "1"
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

                // 1. Handle in-place tile button click if user tapped a button on the home screen
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

                // 3. Build ProtoLayout with interactive buttons
                val rootLayout = buildTileLayout(page, requestParams)

                val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(rootLayout)
                            .build()
                    )
                    .build()

                val tile = TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    .setTileTimeline(
                        TimelineBuilders.Timeline.Builder()
                            .addTimelineEntry(timelineEntry)
                            .build()
                    )
                    .build()

                future.set(tile)
            } catch (e: Exception) {
                val fallbackLayout = PrimaryLayout.Builder(requestParams.deviceConfiguration)
                    .setContent(
                        Text.Builder(applicationContext, "RadialTiles")
                            .setTypography(Typography.TYPOGRAPHY_TITLE3)
                            .setColor(ColorBuilders.argb(0xFFFFB300.toInt()))
                            .build()
                    )
                    .build()

                val tile = TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    .setTileTimeline(
                        TimelineBuilders.Timeline.Builder()
                            .addTimelineEntry(
                                TimelineBuilders.TimelineEntry.Builder()
                                    .setLayout(
                                        LayoutElementBuilders.Layout.Builder()
                                            .setRoot(fallbackLayout)
                                            .build()
                                    )
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

    private fun buildTileLayout(
        page: DialPageConfig,
        requestParams: RequestBuilders.TileRequest
    ): LayoutElementBuilders.LayoutElement {
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

        val contentColumn = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        val buttons = page.buttons

        if (buttons.isEmpty()) {
            contentColumn.addContent(
                CompactChip.Builder(this, "Open App to Setup", titleClickable, requestParams.deviceConfiguration)
                    .build()
            )
        } else {
            // Group buttons into rows of 2
            val chunked = buttons.take(6).chunked(2)
            chunked.forEachIndexed { rowIndex, rowButtons ->
                val rowBuilder = LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

                rowButtons.forEachIndexed { colIndex, btn ->
                    if (colIndex > 0) {
                        rowBuilder.addContent(
                            LayoutElementBuilders.Spacer.Builder()
                                .setWidth(DimensionBuilders.dp(4f))
                                .build()
                        )
                    }

                    val clickAction = ActionBuilders.LoadAction.Builder().build()
                    val buttonClickable = ModifiersBuilders.Clickable.Builder()
                        .setOnClick(clickAction)
                        .setId("toggle:${btn.entityId}:${btn.domain}")
                        .build()

                    val glyph = com.radialtiles.util.IconMapper.getGlyph(btn.iconName, btn.domain) + " "

                    val chipColor = parseArgbColor(btn.colorHex)
                    val chipColors = androidx.wear.protolayout.material.ChipColors(
                        ColorBuilders.argb(chipColor),
                        ColorBuilders.argb(0xFFFFFFFF.toInt())
                    )
                    val chip = CompactChip.Builder(
                        this,
                        "$glyph${btn.name.take(7)}",
                        buttonClickable,
                        requestParams.deviceConfiguration
                    ).setChipColors(chipColors)

                    rowBuilder.addContent(chip.build())
                }

                contentColumn.addContent(rowBuilder.build())
                if (rowIndex < chunked.size - 1) {
                    contentColumn.addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setHeight(DimensionBuilders.dp(4f))
                            .build()
                    )
                }
            }
        }

        return PrimaryLayout.Builder(requestParams.deviceConfiguration)
            .setPrimaryLabelTextContent(
                Text.Builder(this, page.title)
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(ColorBuilders.argb(0xFFFFB300.toInt()))
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setClickable(titleClickable)
                            .build()
                    )
                    .build()
            )
            .setContent(contentColumn.build())
            .build()
    }

    private fun parseArgbColor(hex: String): Int {
        return try {
            val clean = hex.removePrefix("#")
            when (clean.length) {
                6 -> AndroidColor.parseColor("#E6$clean")
                8 -> AndroidColor.parseColor("#$clean")
                else -> 0xFF333333.toInt()
            }
        } catch (e: Exception) {
            0xFF333333.toInt()
        }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val future = ResolvableFuture.create<ResourceBuilders.Resources>()
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
        future.set(resources)
        return future
    }
}
