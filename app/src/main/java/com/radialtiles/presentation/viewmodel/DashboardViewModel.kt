package com.radialtiles.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radialtiles.data.api.HomeAssistantClient
import com.radialtiles.data.model.AppConfiguration
import com.radialtiles.data.repository.TileConfigRepository
import com.radialtiles.feedback.AudioFeedbackManager
import com.radialtiles.feedback.HapticFeedbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TileConfigRepository(application)
    val hapticManager = HapticFeedbackManager(application)
    val audioManager = AudioFeedbackManager()

    val config: StateFlow<AppConfiguration> = repository.configFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppConfiguration())

    private val _entityStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val entityStates: StateFlow<Map<String, Boolean>> = _entityStates.asStateFlow()

    private val haClient = HomeAssistantClient(
        getBaseUrl = { config.value.haBaseUrl },
        getLocalUrl = { config.value.localHaUrl },
        getToken = { config.value.haToken }
    )

    init {
        // Sync haptics/audio settings from config
        viewModelScope.launch {
            config.collect { cfg ->
                hapticManager.isEnabled = cfg.hapticsEnabled
                audioManager.isEnabled = cfg.audioEnabled
                refreshStates()
            }
        }
    }

    fun refreshStates() {
        if (config.value.haBaseUrl.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val entities = haClient.fetchAllStates()
            if (entities.isNotEmpty()) {
                val stateMap = entities.associate { it.entity_id to it.isOn }
                _entityStates.update { it + stateMap }
            }
        }
    }

    fun updateHaptics(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateHapticsEnabled(enabled)
            hapticManager.isEnabled = enabled
        }
    }

    fun updateAudio(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAudioEnabled(enabled)
            audioManager.isEnabled = enabled
        }
    }

    fun saveFullConfig(newConfig: AppConfiguration) {
        viewModelScope.launch {
            repository.saveConfiguration(newConfig)
            com.radialtiles.tile.RadialTileUpdater.requestAllTilesUpdate(getApplication())
            hapticManager.vibrateScene()
            audioManager.playSceneChime()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioManager.release()
    }
}
