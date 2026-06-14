package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class CheatAccusationResult(
    val accuserPlayerId: String,
    val accusedPlayerId: String,
    val caught: Boolean,
    val cheatType: CheatType? = null
)
