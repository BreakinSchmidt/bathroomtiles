package com.radialtiles.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.radialtiles.presentation.theme.AmberAccent
import com.radialtiles.presentation.theme.Black

@Composable
fun SettingsScreen(
    hapticsEnabled: Boolean,
    onHapticsChanged: (Boolean) -> Unit,
    audioEnabled: Boolean,
    onAudioChanged: (Boolean) -> Unit,
    haUrl: String,
    onOpenSetup: () -> Unit,
    onBack: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 20.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.title2,
                color = AmberAccent,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            ToggleChip(
                checked = hapticsEnabled,
                onCheckedChange = onHapticsChanged,
                label = { Text("Haptic Waveforms", fontSize = 12.sp) },
                toggleControl = {
                    Switch(checked = hapticsEnabled)
                },
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            ToggleChip(
                checked = audioEnabled,
                onCheckedChange = onAudioChanged,
                label = { Text("Audio Clicks/Chimes", fontSize = 12.sp) },
                toggleControl = {
                    Switch(checked = audioEnabled)
                },
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Chip(
                onClick = onOpenSetup,
                label = { Text("Phone Setup Portal", fontSize = 12.sp) },
                secondaryLabel = {
                    Text(
                        if (haUrl.isNotBlank()) "HA: ${haUrl.take(20)}..." else "Not configured",
                        fontSize = 10.sp,
                        color = Color.LightGray
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(34.dp),
                colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFF27272A))
            ) {
                Text("Close", color = Color.White)
            }
        }
    }
}
