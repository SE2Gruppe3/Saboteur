package com.aau.saboteur.network

import com.aau.saboteur.BuildConfig

object NetworkConstants {
    private var overridenBaseUrl: String? = null

    fun setBaseUrl(url: String) {
        overridenBaseUrl = url
    }

    val baseUrl: String
        get() = overridenBaseUrl ?: BuildConfig.BASE_URL

    val wsBaseUrl: String
        get() = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        
    val pingEndpoint: String
        get() = "$baseUrl/api/ping"

    val gameWebSocketEndpoint: String
        get() = "$wsBaseUrl/game-ws"
}
