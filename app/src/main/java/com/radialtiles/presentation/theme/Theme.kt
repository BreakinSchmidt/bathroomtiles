package com.radialtiles.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val WearColorPalette = Colors(
    primary = AmberAccent,
    primaryVariant = VioletAccent,
    secondary = CyanAccent,
    background = Black,
    surface = SurfaceDark,
    onPrimary = Black,
    onSecondary = Black,
    onBackground = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color.White
)

@Composable
fun RadialTilesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearColorPalette,
        typography = Typography,
        content = content
    )
}
