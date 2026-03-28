package com.aau.se2game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SaboteurColorScheme = darkColorScheme(
    primary = MineGold,
    onPrimary = SlateShadow,
    secondary = MossGreen,
    onSecondary = LanternCream,
    tertiary = EmberRed,
    onTertiary = LanternCream,
    background = SlateShadow,
    onBackground = LanternCream,
    surface = CaveBrown,
    onSurface = LanternCream,
    surfaceVariant = CaveBrownLight,
    onSurfaceVariant = Color(0xFFE0CBAF),
    outline = Color(0xFF8A6A45)
)

@Composable
fun SE2GameTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SaboteurColorScheme,
        typography = Typography,
        content = content
    )
}
