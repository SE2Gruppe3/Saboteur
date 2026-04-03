package com.aau.server

import com.aau.shared.game.GameState
import com.aau.shared.game.PlayerTurn

val mockPlayerTurns = listOf(
    PlayerTurn(playerId = "1", playerName = "Alice", turnOrder = 1),
    PlayerTurn(playerId = "2", playerName = "Bob", turnOrder = 2),
    PlayerTurn(playerId = "3", playerName = "Charlie", turnOrder = 3),
    PlayerTurn(playerId = "4", playerName = "Diana", turnOrder = 4)
)

val mockGameState = GameState(
    players = mockPlayerTurns,
    currentPlayerId = mockPlayerTurns.firstOrNull()?.playerId
)
