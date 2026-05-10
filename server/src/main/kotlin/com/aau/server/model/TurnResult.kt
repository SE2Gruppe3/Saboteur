package com.aau.server.model

import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.TunnelCard

data class TurnResult(
    val updatedGameState: GameState,
    val updatedHands: Map<String, List<TunnelCard>>
)