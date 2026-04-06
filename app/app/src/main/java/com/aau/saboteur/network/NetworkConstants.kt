package com.aau.saboteur.network

import com.aau.saboteur.BuildConfig

object NetworkConstants {
    private var overriddenBaseUrl: String? = null

    fun setBaseUrl(url: String) {
        overriddenBaseUrl = url
    }

    val baseUrl: String
        get() = overriddenBaseUrl ?: BuildConfig.BASE_URL

    val wsBaseUrl: String
        get() = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        
    val pingEndpoint: String
        get() = "$baseUrl/api/ping"

    val mainWebSocketEndpoint: String
        get() = "$wsBaseUrl/ws"
}
