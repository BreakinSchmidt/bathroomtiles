package com.radialtiles.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.radialtiles.data.model.ButtonConfig
import com.radialtiles.data.model.DialPageConfig
import com.radialtiles.presentation.theme.AmberAccent

@Composable
fun DynamicDialContainer(
    page: DialPageConfig,
    entityStates: Map<String, Boolean>,
    loadingEntityIds: Set<String>,
    onButtonClick: (ButtonConfig) -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            page.buttons.isEmpty() -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "No Buttons",
                        style = MaterialTheme.typography.title2
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onOpenSetup,
                        colors = ButtonDefaults.primaryButtonColors(backgroundColor = AmberAccent)
                    ) {
                        Text("Configure")
                    }
                }
            }
            page.buttons.size in 1..5 -> {
                RadialPieView(
                    buttons = page.buttons,
                    entityStates = entityStates,
                    loadingEntityIds = loadingEntityIds,
                    onButtonClick = onButtonClick
                )
            }
            page.buttons.size >= 6 -> {
                Grid2x3View(
                    buttons = page.buttons.take(6),
                    entityStates = entityStates,
                    loadingEntityIds = loadingEntityIds,
                    onButtonClick = onButtonClick
                )
            }
        }
    }
}
