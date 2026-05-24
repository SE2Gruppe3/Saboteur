package com.aau.saboteur.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

object LanguageManager {

    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    const val LANG_DE = "de"
    const val LANG_EN = "en"
    val SUPPORTED = listOf(LANG_DE, LANG_EN)

    /** Observed by the Compose root to re-provide a localized context on change. */
    val currentLanguage = mutableStateOf(LANG_DE)

    /**
     * Persist the chosen language and notify Compose to recompose with the new locale.
     * No activity.recreate() needed.
     */
    fun setLanguage(context: Context, language: String) {
        require(language in SUPPORTED) { "Unsupported language tag: $language" }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, language).apply()
        currentLanguage.value = language
    }

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANG_DE) ?: LANG_DE

    /**
     * Wrap the base context with the stored locale. Call from Activity.attachBaseContext().
     */
    fun applyToContext(base: Context): Context {
        val locale = Locale(getLanguage(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}