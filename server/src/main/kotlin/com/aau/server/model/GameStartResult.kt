package com.aau.server.model

import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player

data class GameStartResult(
    val gameState: GameState,
    val playerRoles: Map<String, Player>,
    val cardDistribution: CardDistributionResult
)
