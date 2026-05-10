package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayRockfallCardRequest(
    val playerId: String,
    val cardId: String,
    val targetPosition: BoardPosition
)