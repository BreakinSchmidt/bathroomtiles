package com.radialtiles.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ButtonConfig(
    val id: String,
    val entityId: String,
    val name: String,
    val colorHex: String = "#FFB300",
    val iconName: String = "lightbulb",
    val domain: String = inferDomain(entityId)
) {
    companion object {
        fun inferDomain(entityId: String): String {
            return if (entityId.contains(".")) {
                entityId.substringBefore(".")
            } else {
                "light"
            }
        }
    }
}
