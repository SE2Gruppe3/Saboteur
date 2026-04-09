package com.aau.saboteur.ui.theme

import android.app.Activity
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

//Saboteur-Farben
val DwarfGold = Color(0xFFD4AF37)
val StoneGrey = Color(0xFF2B2B2B)
val DeepCoal = Color(0xFF1A1A1A)
val IronWhite = Color(0xFFE0E0E0)




private val DarkColorScheme = darkColorScheme(
    primary = DwarfGold,
    secondary = DwarfGold,
    tertiary = Pink80,
    background = StoneGrey,
    surface = DeepCoal,
    onPrimary = DeepCoal,
    onBackground = IronWhite,
    onSurface = IronWhite,
    outline = DwarfGold
)

private val LightColorScheme = lightColorScheme(
    primary = DwarfGold,
    secondary = DwarfGold,
    tertiary = Pink40,
    background = StoneGrey,
    surface = DeepCoal,
    onPrimary = DeepCoal,
    onBackground = IronWhite,
    onSurface = IronWhite,
    outline = DwarfGold
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
        // Wenn darkTheme true ist -> DarkColorScheme
        // Wenn darkTheme false ist -> LightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}