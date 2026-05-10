package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayRepairCardRequest(
    val playerId: String,
    val cardId: String,
    val targetPlayerId: String,
    val tool: ToolType
)