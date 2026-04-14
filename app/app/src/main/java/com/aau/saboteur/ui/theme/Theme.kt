package com.aau.saboteur.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

private val SaboteurColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = MineCoal,
    secondary = OreCopper,
    onSecondary = Quartz,
    tertiary = MossyGreen,
    onTertiary = Quartz,
    background = MineCoal,
    onBackground = Quartz,
    surface = DarkBrown,
    onSurface = Quartz,
    surfaceVariant = MineSlate,
    onSurfaceVariant = Color(0xFFE5D8BC),
    outline = OreGold
)

@Composable
fun SE2GameTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SaboteurColorScheme,
        typography = Typography,
        content = content
    )
}
