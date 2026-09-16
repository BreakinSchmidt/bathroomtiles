package com.radialtiles

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.radialtiles.data.server.WebConfigServer
import com.radialtiles.presentation.screens.ConfigServerScreen
import com.radialtiles.presentation.screens.DashboardScreen
import com.radialtiles.presentation.screens.SettingsScreen
import com.radialtiles.presentation.theme.RadialTilesTheme
import com.radialtiles.presentation.viewmodel.DashboardViewModel

enum class AppScreen {
    DASHBOARD,
    SETTINGS,
    CONFIG_SERVER
}

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private var webServer: WebConfigServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while interacting with tiles
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize embedded web server for phone configurator
        webServer = WebConfigServer(
            context = applicationContext,
            port = 8080,
            getCurrentConfig = { viewModel.config.value },
            onConfigSaved = { newConfig ->
                viewModel.saveFullConfig(newConfig)
            }
        )

        setContent {
            RadialTilesTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }

                val config by viewModel.config.collectAsState()
                val entityStates by viewModel.entityStates.collectAsState()
                val loadingEntityIds by viewModel.loadingEntityIds.collectAsState()

                when (currentScreen) {
                    AppScreen.DASHBOARD -> {
                        DashboardScreen(
                            config = config,
                            entityStates = entityStates,
                            loadingEntityIds = loadingEntityIds,
                            onButtonClick = { button ->
                                viewModel.onButtonClick(button)
                            },
                            onOpenSettings = {
                                currentScreen = AppScreen.SETTINGS
                            },
                            onOpenSetup = {
                                webServer?.start()
                                currentScreen = AppScreen.CONFIG_SERVER
                            },
                            onCrownTick = {
                                viewModel.hapticManager.vibrateCrownTick()
                            }
                        )
                    }

                    AppScreen.SETTINGS -> {
                        SettingsScreen(
                            hapticsEnabled = config.hapticsEnabled,
                            onHapticsChanged = { viewModel.updateHaptics(it) },
                            audioEnabled = config.audioEnabled,
                            onAudioChanged = { viewModel.updateAudio(it) },
                            haUrl = config.haBaseUrl,
                            onOpenSetup = {
                                webServer?.start()
                                currentScreen = AppScreen.CONFIG_SERVER
                            },
                            onBack = {
                                currentScreen = AppScreen.DASHBOARD
                            }
                        )
                    }

                    AppScreen.CONFIG_SERVER -> {
                        webServer?.let { srv ->
                            ConfigServerScreen(
                                server = srv,
                                onDone = {
                                    srv.stop()
                                    viewModel.refreshStates()
                                    currentScreen = AppScreen.DASHBOARD
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStates()
    }

    override fun onDestroy() {
        super.onDestroy()
        webServer?.stop()
    }
}
