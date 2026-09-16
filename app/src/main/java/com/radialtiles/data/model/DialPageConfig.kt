package com.radialtiles.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DialPageConfig(
    val id: String,
    val title: String,
    val buttons: List<ButtonConfig>
) {
    /**
     * Determines whether this page uses the pie chart layout (1 to 5 buttons)
     * or the 2x3 grid layout (6 buttons).
     */
    val isPieLayout: Boolean
        get() = buttons.size in 1..5

    val isGridLayout: Boolean
        get() = buttons.size == 6
}

@Serializable
data class AppConfiguration(
    val haBaseUrl: String = "",
    val haToken: String = "",
    val localHaUrl: String = "",
    val hapticsEnabled: Boolean = true,
    val audioEnabled: Boolean = true,
    val pages: List<DialPageConfig> = defaultPages()
) {
    companion object {
        fun defaultPages(): List<DialPageConfig> {
            return listOf(
                DialPageConfig(
                    id = "bathroom_main",
                    title = "Bathroom",
                    buttons = listOf(
                        ButtonConfig(
                            id = "btn_1",
                            entityId = "light.bathroom_ceiling",
                            name = "Ceiling",
                            colorHex = "#FFB300",
                            iconName = "lightbulb",
                            domain = "light"
                        ),
                        ButtonConfig(
                            id = "btn_2",
                            entityId = "light.bathroom_mirror",
                            name = "Mirror",
                            colorHex = "#00E676",
                            iconName = "lightbulb",
                            domain = "light"
                        ),
                        ButtonConfig(
                            id = "btn_3",
                            entityId = "switch.bathroom_fan",
                            name = "Fan",
                            colorHex = "#00E5FF",
                            iconName = "fan",
                            domain = "switch"
                        ),
                        ButtonConfig(
                            id = "btn_4",
                            entityId = "scene.bathroom_relax",
                            name = "Relax",
                            colorHex = "#7C4DFF",
                            iconName = "scene",
                            domain = "scene"
                        )
                    )
                )
            )
        }
    }
}
