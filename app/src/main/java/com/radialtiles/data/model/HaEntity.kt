package com.radialtiles.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class HaEntity(
    val entity_id: String,
    val state: String = "off",
    val attributes: JsonObject? = null,
    val last_changed: String? = null
) {
    val friendlyName: String
        get() = attributes?.get("friendly_name")?.toString()?.trim('"') 
            ?: entity_id.substringAfter(".")
                .replace("_", " ")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    val isOn: Boolean
        get() = state.equals("on", ignoreCase = true)
}
