package com.aau.server.model

import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.TunnelCard

/**
 * Carries the outcome of a single player turn.
 *
 * @property updatedGameState the board and turn state after the action
 * @property updatedHands each player's hand after the action
 * @property winner non-null when the game has ended: "DWARVES" if the gold goal was reached,
 *   "SABOTEURS" if all players passed after the draw pile was exhausted, null otherwise
 */
data class TurnResult(
    val updatedGameState: GameState,
    val updatedHands: Map<String, List<TunnelCard>>,
    val winner: String? = null
)
