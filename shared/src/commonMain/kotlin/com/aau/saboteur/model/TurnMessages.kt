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

@Serializable
data class PlayBlockCardRequest(
    val playerId: String,
    val cardId: String,
    val targetPlayerId: String
)

@Serializable
data class PlayRepairCardRequest(
    val playerId: String,
    val cardId: String,
    val targetPlayerId: String,
    val tool: ToolType
)

@Serializable
data class PlayMapCardRequest(
    val playerId: String,
    val cardId: String,
    val targetPosition: BoardPosition
)

@Serializable
data class PlayRockfallCardRequest(
    val playerId: String,
    val cardId: String,
    val targetPosition: BoardPosition
)
