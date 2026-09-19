package com.radialtiles.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radialtiles.data.api.HomeAssistantClient
import com.radialtiles.data.api.HomeAssistantWebSocket
import com.radialtiles.data.model.AppConfiguration
import com.radialtiles.data.model.ButtonConfig
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

    private val _loadingEntityIds = MutableStateFlow<Set<String>>(emptySet())
    val loadingEntityIds: StateFlow<Set<String>> = _loadingEntityIds.asStateFlow()

    private val haClient = HomeAssistantClient(
        getBaseUrl = { config.value.haBaseUrl },
        getLocalUrl = { config.value.localHaUrl },
        getToken = { config.value.haToken }
    )

    private var haWebSocket: HomeAssistantWebSocket? = null

    init {
        // Sync haptics/audio settings from config
        viewModelScope.launch {
            config.collect { cfg ->
                hapticManager.isEnabled = cfg.hapticsEnabled
                audioManager.isEnabled = cfg.audioEnabled
                restartWebSocket()
                refreshStates()
            }
        }
    }

    private fun restartWebSocket() {
        haWebSocket?.disconnect()
        if (config.value.haBaseUrl.isNotBlank() && config.value.haToken.isNotBlank()) {
            haWebSocket = HomeAssistantWebSocket(
                getBaseUrl = { config.value.haBaseUrl },
                getToken = { config.value.haToken },
                onStateChanged = { entityId, newState ->
                    val isOn = newState.equals("on", ignoreCase = true)
                    _entityStates.update { it + (entityId to isOn) }
                }
            ).also { it.connect() }
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

    fun onButtonClick(button: ButtonConfig) {
        val currentState = _entityStates.value[button.entityId] ?: false
        val isSceneOrAutomation = button.domain in listOf("scene", "automation", "script")
        val targetState = if (isSceneOrAutomation) true else !currentState

        // Multi-sensory feedback immediately on tap
        if (isSceneOrAutomation) {
            hapticManager.vibrateScene()
            audioManager.playSceneChime()
        } else if (targetState) {
            hapticManager.vibrateToggleOn()
            audioManager.playClickOn()
        } else {
            hapticManager.vibrateToggleOff()
            audioManager.playClickOff()
        }

        // Optimistic UI update
        _entityStates.update { it + (button.entityId to targetState) }
        _loadingEntityIds.update { it + button.entityId }

        viewModelScope.launch(Dispatchers.IO) {
            val success = haClient.toggleEntity(button.entityId, button.domain)
            _loadingEntityIds.update { it - button.entityId }

            if (!success) {
                // Revert optimistic update & trigger error feedback
                _entityStates.update { it + (button.entityId to currentState) }
                hapticManager.vibrateError()
                audioManager.playErrorTone()
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
        haWebSocket?.disconnect()
        audioManager.release()
    }
}
