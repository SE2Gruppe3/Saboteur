package com.aau.saboteur.network.lobby

import com.aau.saboteur.model.LobbyState
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    coerceInputValues = true
}

fun String.toLobbyState(): LobbyState = json.decodeFromString(this)