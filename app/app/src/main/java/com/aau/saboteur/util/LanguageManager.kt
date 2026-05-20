package com.aau.saboteur.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageManager {

    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    const val LANG_DE = "de"
    const val LANG_EN = "en"
    val SUPPORTED = listOf(LANG_DE, LANG_EN)

    /**
     * Persist the chosen language. Call activity.recreate() afterwards to apply.
     */
    fun setLanguage(context: Context, language: String) {
        require(language in SUPPORTED) { "Unsupported language tag: $language" }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, language).apply()
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