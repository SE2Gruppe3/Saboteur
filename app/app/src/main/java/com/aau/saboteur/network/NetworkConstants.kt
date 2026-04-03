package com.aau.saboteur.network

import com.aau.saboteur.BuildConfig

object NetworkConstants {
    private var overridenBaseUrl: String? = null

    fun setBaseUrl(url: String) {
        overridenBaseUrl = url
    }

    val baseUrl: String
        get() = overridenBaseUrl ?: BuildConfig.BASE_URL

    val pingEndpoint: String
        get() = "$baseUrl/api/ping"
    val gameStateEndpoint: String
        get() = "$baseUrl/api/game"
    val startGameEndpoint: String
        get() = "$baseUrl/api/game/start"
}
