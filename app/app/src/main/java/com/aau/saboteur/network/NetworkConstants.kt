package com.aau.saboteur.network

import com.aau.saboteur.BuildConfig

object NetworkConstants {
    private var overriddenBaseUrl: String? = null

    /**
     * Overrides the base URL for testing or dynamic configuration.
     */
    fun setBaseUrl(url: String) {
        overriddenBaseUrl = if (url.isEmpty()) null else url
    }

    val baseUrl: String
        get() = overriddenBaseUrl ?: BuildConfig.BASE_URL

    val wsBaseUrl: String
        get() = baseUrl.replace("http://", "ws://").replace("https://", "wss://")

    val mainWebSocketEndpoint: String
        get() = "$wsBaseUrl/ws"
}
