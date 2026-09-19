package com.radialtiles.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.radialtiles.data.model.AppConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "radial_tiles_prefs")

class TileConfigRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    companion object {
        val KEY_CONFIG_JSON = stringPreferencesKey("app_config_json")
    }

    val configFlow: Flow<AppConfiguration> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val jsonString = preferences[KEY_CONFIG_JSON]
            if (!jsonString.isNullOrBlank()) {
                try {
                    json.decodeFromString<AppConfiguration>(jsonString)
                } catch (e: Exception) {
                    AppConfiguration()
                }
            } else {
                AppConfiguration()
            }
        }

    suspend fun saveConfiguration(config: AppConfiguration) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CONFIG_JSON] = json.encodeToString(config)
        }
        com.radialtiles.tile.RadialTileUpdater.requestAllTilesUpdate(context)
    }

    suspend fun updateHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val current = loadCurrentConfig(preferences)
            preferences[KEY_CONFIG_JSON] = json.encodeToString(current.copy(hapticsEnabled = enabled))
        }
    }

    suspend fun updateAudioEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val current = loadCurrentConfig(preferences)
            preferences[KEY_CONFIG_JSON] = json.encodeToString(current.copy(audioEnabled = enabled))
        }
    }

    private fun loadCurrentConfig(preferences: Preferences): AppConfiguration {
        val jsonString = preferences[KEY_CONFIG_JSON]
        return if (!jsonString.isNullOrBlank()) {
            try {
                json.decodeFromString<AppConfiguration>(jsonString)
            } catch (e: Exception) {
                AppConfiguration()
            }
        } else {
            AppConfiguration()
        }
    }
}
