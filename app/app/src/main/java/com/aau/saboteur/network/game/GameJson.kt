package com.aau.saboteur.network.game

import com.aau.saboteur.model.CreateGameRequest
import com.aau.saboteur.model.GameState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    coerceInputValues = true
}

fun CreateGameRequest.toJson(): String = json.encodeToString(this)

fun String.toGameState(): GameState = json.decodeFromString(this)
