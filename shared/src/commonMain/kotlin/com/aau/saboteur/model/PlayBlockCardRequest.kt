package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayBlockCardRequest(
    val playerId: String,
    val cardId: String,
    val targetPlayerId: String
)