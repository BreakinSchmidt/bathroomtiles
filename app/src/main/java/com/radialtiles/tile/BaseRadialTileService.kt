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

        val titleModifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(titleClickable)
            .build()

        val titleText = Text.Builder(this, page.title.uppercase())
            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
            .setColor(ColorBuilders.argb(0xFFF59E0B.toInt()))
            .setModifiers(titleModifiers)
            .build()

        val buttons = page.buttons
        val buttonsLayout = buildButtonsLayout(buttons)

        val mainColumn = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(10f))
                    .build()
            )
            .addContent(titleText)
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(5f))
                    .build()
            )
            .addContent(buttonsLayout)
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .addContent(mainColumn)
            .build()
    }

    private fun buildButtonsLayout(buttons: List<ButtonConfig>): LayoutElementBuilders.LayoutElement {
        if (buttons.isEmpty()) {
            return Text.Builder(this, "Tap to setup in app").build()
        }

        return when (buttons.size) {
            1 -> {
                buildDialButton(buttons[0], widthDp = 135f, heightDp = 135f, cornerRadiusDp = 67f, iconTypography = Typography.TYPOGRAPHY_DISPLAY3)
            }
            2 -> {
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(buildDialButton(buttons[0], widthDp = 150f, heightDp = 68f, cornerRadiusDp = 28f))
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(5f)).build())
                    .addContent(buildDialButton(buttons[1], widthDp = 150f, heightDp = 68f, cornerRadiusDp = 28f))
                    .build()
            }
            3 -> {
                val row1 = LayoutElementBuilders.Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(buildDialButton(buttons[0], widthDp = 86f, heightDp = 68f, cornerRadiusDp = 22f))
                    .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(5f)).build())
                    .addContent(buildDialButton(buttons[1], widthDp = 86f, heightDp = 68f, cornerRadiusDp = 22f))
                    .build()

                val row2 = buildDialButton(buttons[2], widthDp = 135f, heightDp = 62f, cornerRadiusDp = 22f)

                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(row1)
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(5f)).build())
                    .addContent(row2)
                    .build()
            }
            4 -> {
                // 2x2 Quadrant layout (Matching the 4-quadrant radial dial)
                val row1 = LayoutElementBuilders.Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(buildDialButton(buttons[0], widthDp = 88f, heightDp = 68f, cornerRadiusDp = 24f))
                    .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(5f)).build())
                    .addContent(buildDialButton(buttons[1], widthDp = 88f, heightDp = 68f, cornerRadiusDp = 24f))
                    .build()

                val row2 = LayoutElementBuilders.Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(buildDialButton(buttons[2], widthDp = 88f, heightDp = 68f, cornerRadiusDp = 24f))
                    .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(5f)).build())
                    .addContent(buildDialButton(buttons[3], widthDp = 88f, heightDp = 68f, cornerRadiusDp = 24f))
                    .build()

                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(row1)
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(5f)).build())
                    .addContent(row2)
                    .build()
            }
            5 -> {
                val row1 = LayoutElementBuilders.Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(buildDialButton(buttons[0], widthDp = 82f, heightDp = 46f, cornerRadiusDp = 18f))
                    .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(4f)).build())
                    .addContent(buildDialButton(buttons[1], widthDp = 82f, heightDp = 46f, cornerRadiusDp = 18f))
                    .build()

                val row2 = buildDialButton(buttons[2], widthDp = 125f, heightDp = 44f, cornerRadiusDp = 18f)

                val row3 = LayoutElementBuilders.Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(buildDialButton(buttons[3], widthDp = 82f, heightDp = 46f, cornerRadiusDp = 18f))
                    .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(4f)).build())
                    .addContent(buildDialButton(buttons[4], widthDp = 82f, heightDp = 46f, cornerRadiusDp = 18f))
                    .build()

                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(row1)
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
                    .addContent(row2)
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
                    .addContent(row3)
                    .build()
            }
            else -> {
                // 6 Buttons: 2x3 Contoured Grid
                val col = LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

                val chunked = buttons.take(6).chunked(2)
                chunked.forEachIndexed { rIdx, rowButtons ->
                    val row = LayoutElementBuilders.Row.Builder()
                        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)

                    val w = if (rIdx == 1) 90f else 80f
                    rowButtons.forEachIndexed { cIdx, b ->
                        if (cIdx > 0) {
                            row.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(4f)).build())
                        }
                        row.addContent(buildDialButton(b, widthDp = w, heightDp = 44f, cornerRadiusDp = 16f))
                    }
                    col.addContent(row.build())
                    if (rIdx < chunked.size - 1) {
                        col.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(3f)).build())
                    }
                }
                col.build()
            }
        }
    }

    private fun buildDialButton(
        btn: ButtonConfig,
        widthDp: Float,
        heightDp: Float,
        cornerRadiusDp: Float,
        iconTypography: Int = Typography.TYPOGRAPHY_TITLE2
    ): LayoutElementBuilders.LayoutElement {
        val clickAction = ActionBuilders.LoadAction.Builder().build()
        val buttonClickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(clickAction)
            .setId("toggle:${btn.entityId}:${btn.domain}")
            .build()

        val baseColor = parseArgbColor(btn.colorHex)
        val bgColor = (baseColor and 0x00FFFFFF) or 0x48000000.toInt()
        val borderColor = (baseColor and 0x00FFFFFF) or 0xAA000000.toInt()

        val corner = ModifiersBuilders.Corner.Builder()
            .setRadius(DimensionBuilders.dp(cornerRadiusDp))
            .build()

        val background = ModifiersBuilders.Background.Builder()
            .setColor(ColorBuilders.argb(bgColor))
            .setCorner(corner)
            .build()

        val border = ModifiersBuilders.Border.Builder()
            .setColor(ColorBuilders.argb(borderColor))
            .setWidth(DimensionBuilders.dp(1.5f))
            .build()

        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setBackground(background)
            .setBorder(border)
            .setClickable(buttonClickable)
            .build()

        val glyph = com.radialtiles.util.IconMapper.getGlyph(btn.iconName, btn.domain)

        val iconText = Text.Builder(this, glyph)
            .setTypography(iconTypography)
            .build()

        val labelText = Text.Builder(this, btn.name.take(7))
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
            .build()

        val col = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(iconText)
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(2f))
                    .build()
            )
            .addContent(labelText)
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(widthDp))
            .setHeight(DimensionBuilders.dp(heightDp))
            .setModifiers(modifiers)
            .addContent(col)
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
