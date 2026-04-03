package com.aau.shared.game

data class Player(
    val id: String = "",
    val name: String = ""
)

data class PlayerTurn(
    val playerId: String = "",
    val playerName: String = "",
    val turnOrder: Int = 0
)

data class GameState(
    val players: List<PlayerTurn> = emptyList(),
    val currentPlayerId: String? = null
)

data class CreateGameRequest(
    val players: List<Player> = emptyList()
)
