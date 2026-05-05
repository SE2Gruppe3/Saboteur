package com.aau.saboteur.network

import com.aau.saboteur.BuildConfig

object NetworkConstants {
    private var overriddenBaseUrl: String? = null

    val baseUrl: String
        get() = overriddenBaseUrl ?: BuildConfig.BASE_URL

    val wsBaseUrl: String
        get() = baseUrl.replace("http://", "ws://").replace("https://", "wss://")

    val mainWebSocketEndpoint: String
        get() = "$wsBaseUrl/ws"
}
