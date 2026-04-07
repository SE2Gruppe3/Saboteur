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


val DwarfGold = Color(0xFFD4AF37)
val StoneGrey = Color(0xFF2B2B2B)
val DeepCoal = Color(0xFF1A1A1A)
val IronWhite = Color(0xFFE0E0E0)

private val DarkColorScheme = darkColorScheme(
    primary = DwarfGold,       // Gold
    secondary = DwarfGold,     // Gold
    tertiary = Pink80,
    background = StoneGrey,    // Stein-Grau
    surface = DeepCoal,        // dunkles Anthrazit
    onPrimary = DeepCoal,
    onBackground = IronWhite,
    onSurface = IronWhite,
    outline = DwarfGold        // Rahmen der Textfelder in Gold
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

)

@Composable
fun SE2GameTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}