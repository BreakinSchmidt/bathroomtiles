package com.radialtiles

import com.radialtiles.data.model.AppConfiguration
import com.radialtiles.data.model.ButtonConfig
import com.radialtiles.data.model.DialPageConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    @Test
    fun testAppConfigurationSerializationRoundTrip() {
        val config = AppConfiguration(
            haBaseUrl = "https://test.ui.nabu.casa",
            haToken = "sample_token_12345",
            localHaUrl = "http://192.168.1.150:8123",
            hapticsEnabled = true,
            audioEnabled = false,
            pages = listOf(
                DialPageConfig(
                    id = "test_page_1",
                    title = "Master Bath",
                    buttons = listOf(
                        ButtonConfig("b1", "light.ceiling", "Ceiling", "#FFB300", "lightbulb", "light"),
                        ButtonConfig("b2", "switch.fan", "Fan", "#00E5FF", "fan", "switch")
                    )
                )
            )
        )

        val serialized = json.encodeToString(config)
        val deserialized = json.decodeFromString<AppConfiguration>(serialized)

        assertEquals("https://test.ui.nabu.casa", deserialized.haBaseUrl)
        assertEquals("sample_token_12345", deserialized.haToken)
        assertEquals("http://192.168.1.150:8123", deserialized.localHaUrl)
        assertTrue(deserialized.hapticsEnabled)
        assertEquals(false, deserialized.audioEnabled)
        assertEquals(1, deserialized.pages.size)
        assertEquals("Master Bath", deserialized.pages[0].title)
        assertEquals(2, deserialized.pages[0].buttons.size)
        assertTrue(deserialized.pages[0].isPieLayout)
    }

    @Test
    fun testGridLayoutFlagForSixButtons() {
        val sixButtons = (1..6).map {
            ButtonConfig("b$it", "light.light_$it", "L$it", "#00E676", "lightbulb", "light")
        }
        val page = DialPageConfig("grid_page", "Grid Room", sixButtons)
        assertTrue(page.isGridLayout)
        assertEquals(false, page.isPieLayout)
    }
}
