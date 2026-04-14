package com.aau.saboteur.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
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

private val LightColorScheme = lightColorScheme(
    primary = Gold,
    onPrimary = Quartz,
    secondary = OreCopper,
    onSecondary = Quartz,
    tertiary = MossyGreen,
    onTertiary = Quartz,
    background = Parchment,
    onBackground = MineCoal,
    surface = Quartz,
    onSurface = MineCoal,
    surfaceVariant = Color(0xFFE6D5B1),
    onSurfaceVariant = DarkBrown,
    outline = OreCopper
)

@Composable
fun SE2GameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
