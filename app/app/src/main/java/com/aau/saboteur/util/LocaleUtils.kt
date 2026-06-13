package com.aau.saboteur.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun rememberLocalizedContext(language: String): Context {
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(language, configuration) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(configuration)
        config.setLocale(locale)
        base.createConfigurationContext(config)
    }
}
