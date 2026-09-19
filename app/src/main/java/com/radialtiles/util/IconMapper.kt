package com.radialtiles.util

object IconMapper {
    /**
     * Maps Home Assistant MDI icon names, keywords, or entity domains to a crisp Unicode glyph.
     */
    fun getGlyph(iconName: String, domain: String = ""): String {
        val clean = iconName.trim().lowercase().removePrefix("mdi:")

        return when {
            clean.isEmpty() -> domainFallback(domain)

            // Lights & Lamps
            clean in listOf("lightbulb", "light", "bulb", "lightbulb-outline") -> "💡"
            clean in listOf("ceiling-light", "ceiling", "lantern") -> "🏮"
            clean in listOf("wall-sconce", "sconce", "diya") -> "🪔"
            clean in listOf("lamp", "desk-lamp", "table-lamp", "floor-lamp") -> "🛋️"
            clean in listOf("chandelier", "disco") -> "🪩"
            clean in listOf("candle") -> "🕯️"
            clean in listOf("spotlight", "flashlight", "torch") -> "🔦"
            clean in listOf("led-strip", "led") -> "〰️"
            clean.contains("light") || clean.contains("lamp") || clean.contains("bulb") -> "💡"

            // Climate, HVAC & Heating
            clean in listOf("fan", "exhaust-fan", "ceiling-fan", "vent", "fan-speed-1", "fan-speed-2", "fan-speed-3") -> "🌀"
            clean in listOf("air-conditioner", "ac", "cooling", "snowflake", "freeze", "cold") -> "❄️"
            clean in listOf("radiator", "heater", "heat", "hot-tub") -> "♨️"
            clean in listOf("fire", "fireplace", "flame") -> "🔥"
            clean in listOf("thermometer", "temperature", "thermostat", "temp") -> "🌡️"
            clean in listOf("humidifier", "humidity") -> "💧"
            clean in listOf("air-purifier", "purifier", "leaf") -> "🍃"
            clean.contains("fan") -> "🌀"
            clean.contains("heat") || clean.contains("radiat") || clean.contains("warm") -> "♨️"
            clean.contains("cool") || clean.contains("snow") || clean.contains("ac") -> "❄️"
            clean.contains("thermo") || clean.contains("temp") -> "🌡️"

            // Water & Bathroom
            clean in listOf("shower", "shower-head") -> "🚿"
            clean in listOf("bathtub", "bath", "tub") -> "🛁"
            clean in listOf("water", "water-drop") -> "💧"
            clean in listOf("water-boiler", "water-heater", "boiler", "kettle") -> "🫖"
            clean in listOf("faucet", "tap") -> "🚰"
            clean in listOf("toilet") -> "🚽"
            clean in listOf("pool") -> "🏊"
            clean in listOf("sprinkler") -> "💦"
            clean.contains("shower") -> "🚿"
            clean.contains("bath") || clean.contains("tub") -> "🛁"
            clean.contains("water") -> "💧"

            // Power & Electrical
            clean in listOf("power", "switch", "toggle", "toggle-switch") -> "⏻"
            clean in listOf("power-plug", "plug", "outlet", "power-socket") -> "🔌"
            clean in listOf("flash", "lightning", "energy", "electric") -> "⚡"
            clean in listOf("battery", "battery-charging") -> "🔋"
            clean.contains("plug") || clean.contains("outlet") -> "🔌"

            // Media & Entertainment
            clean in listOf("television", "tv", "monitor", "display") -> "📺"
            clean in listOf("speaker", "speaker-wireless", "volume", "volume-high") -> "🔊"
            clean in listOf("volume-off", "volume-mute", "mute") -> "🔇"
            clean in listOf("music", "music-note", "audio") -> "🎵"
            clean in listOf("radio") -> "📻"
            clean in listOf("movie", "film", "projector") -> "🎬"
            clean in listOf("gamepad", "controller", "game", "console") -> "🎮"
            clean in listOf("play", "pause") -> "▶️"
            clean.contains("tv") || clean.contains("screen") -> "📺"
            clean.contains("speaker") || clean.contains("sound") || clean.contains("volume") -> "🔊"
            clean.contains("music") -> "🎵"

            // Bedroom & Furniture
            clean in listOf("bed", "bedroom", "sleep") -> "🛏️"
            clean in listOf("sofa", "couch", "living-room") -> "🛋️"
            clean in listOf("desk", "office", "computer") -> "🖥️"
            clean in listOf("laptop") -> "💻"
            clean in listOf("table-chair", "table", "chair", "dining") -> "🪑"
            clean.contains("bed") -> "🛏️"
            clean.contains("sofa") || clean.contains("couch") -> "🛋️"

            // Doors, Windows & Access
            clean in listOf("door", "door-open", "door-closed") -> "🚪"
            clean in listOf("window", "window-closed", "window-open") -> "🪟"
            clean in listOf("blinds", "curtains", "curtain", "shade", "shutter") -> "🪟"
            clean in listOf("garage", "garage-open", "garage-closed") -> "🚗"
            clean in listOf("gate") -> "⛩️"
            clean.contains("door") -> "🚪"
            clean.contains("window") || clean.contains("blind") || clean.contains("curtain") -> "🪟"
            clean.contains("garage") -> "🚗"

            // Security & Safety
            clean in listOf("lock", "lock-closed", "locked") -> "🔒"
            clean in listOf("lock-open", "unlocked") -> "🔓"
            clean in listOf("key") -> "🔑"
            clean in listOf("shield", "shield-home", "security") -> "🛡️"
            clean in listOf("cctv", "camera", "security-camera", "webcam") -> "📹"
            clean in listOf("bell", "doorbell") -> "🔔"
            clean in listOf("siren", "alarm") -> "🚨"
            clean in listOf("motion-sensor", "motion", "run") -> "🏃"
            clean in listOf("smoke-detector", "warning", "alert") -> "⚠️"
            clean.contains("lock") -> "🔒"
            clean.contains("camera") -> "📹"
            clean.contains("shield") || clean.contains("secur") -> "🛡️"
            clean.contains("bell") -> "🔔"

            // Kitchen & Appliances
            clean in listOf("coffee", "coffee-maker") -> "☕"
            clean in listOf("refrigerator", "fridge") -> "🧊"
            clean in listOf("microwave", "oven", "stove", "cook") -> "🍳"
            clean in listOf("dishwasher") -> "🍽️"
            clean in listOf("washing-machine", "laundry", "washer") -> "🧺"
            clean in listOf("robot-vacuum", "vacuum", "clean", "broom") -> "🧹"
            clean.contains("coffee") -> "☕"
            clean.contains("vacuum") || clean.contains("clean") -> "🧹"

            // Scenes, Automations & Celestial
            clean in listOf("sparkles", "scene", "magic") -> "✨"
            clean in listOf("auto-fix", "wand") -> "🪄"
            clean in listOf("robot", "automation") -> "🤖"
            clean in listOf("clock", "timer", "stopwatch") -> "⏱️"
            clean in listOf("weather-sunny", "sun", "day", "bright") -> "☀️"
            clean in listOf("weather-night", "moon", "night", "nightlight") -> "🌙"
            clean.contains("scene") || clean.contains("sparkle") -> "✨"
            clean.contains("auto") -> "⚡"
            clean.contains("sun") -> "☀️"
            clean.contains("moon") || clean.contains("night") -> "🌙"

            // Plants & Garden
            clean in listOf("flower", "plant", "garden", "pot") -> "🪴"
            clean in listOf("tree", "forest", "yard") -> "🌲"
            clean in listOf("car", "ev", "charger") -> "🚗"

            else -> domainFallback(domain)
        }
    }

    private fun domainFallback(domain: String): String {
        return when (domain.lowercase()) {
            "light" -> "💡"
            "fan" -> "🌀"
            "climate" -> "🌡️"
            "scene" -> "✨"
            "automation" -> "⚡"
            "switch" -> "⏻"
            "media_player" -> "📺"
            "cover" -> "🪟"
            "lock" -> "🔒"
            "vacuum" -> "🧹"
            "camera" -> "📹"
            "water_heater" -> "🚿"
            else -> "⏻"
        }
    }
}
