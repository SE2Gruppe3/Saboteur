package com.aau.server.model

import com.aau.saboteur.model.CheatAccusationResult
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.TunnelCard

data class CheatAccusationTurnResult(
    val accusation: CheatAccusationResult,
    val updatedGameState: GameState,
    val updatedHands: Map<String, List<TunnelCard>>
)
