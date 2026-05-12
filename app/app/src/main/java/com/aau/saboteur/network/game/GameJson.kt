package com.aau.saboteur.network.game

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CreateGameRequest
import com.aau.saboteur.model.DiscardCardRequest
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.PlayCardRequest
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.TunnelCard
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    coerceInputValues = true
}

fun CreateGameRequest.toJson(): String = json.encodeToString(this)
fun PlayCardRequest.toJson(): String = json.encodeToString(this)
fun DiscardCardRequest.toJson(): String = json.encodeToString(this)

fun String.toGameState(): GameState = json.decodeFromString(this)
fun String.toPlayer(): Player = json.decodeFromString(this)
fun String.toHands(): Map<String, List<TunnelCard>> = json.decodeFromString(this)

@Serializable
private data class ValidPositionsResponse(val positions: List<BoardPosition>)

fun String.toValidPositions(): List<BoardPosition> =
    json.decodeFromString<ValidPositionsResponse>(this).positions