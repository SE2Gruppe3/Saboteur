package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerTurn(
    val playerId: String = "",
    val playerName: String = "",
    val turnOrder: Int = 0
)
