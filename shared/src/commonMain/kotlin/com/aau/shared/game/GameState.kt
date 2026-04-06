package com.aau.shared.game

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: String = "",
    val name: String = ""
)

@Serializable
data class PlayerTurn(
    val playerId: String = "",
    val playerName: String = "",
    val turnOrder: Int = 0
)

@Serializable
data class GameState(
    val players: List<PlayerTurn> = emptyList(),
    val currentPlayerId: String? = null
)

@Serializable
data class CreateGameRequest(
    val players: List<Player> = emptyList()
)
