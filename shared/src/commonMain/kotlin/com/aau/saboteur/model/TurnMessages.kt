package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayCardRequest(
    val playerId: String,
    val cardId: String,
    val position: BoardPosition,
    val isRotated: Boolean = false
)

@Serializable
data class DiscardCardRequest(
    val playerId: String,
    val cardId: String
)