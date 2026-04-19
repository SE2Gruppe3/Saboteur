package com.aau.server

import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.TunnelCard
import com.aau.server.model.CardDistributionResult

/**
 * Holds the complete state of a running Saboteur game.
 *
 * [hands] and [drawPile] track only cards currently in players' hands or on the draw pile.
 * Cards placed on the board ([GameBoard]) or discarded are no longer tracked here — their
 * count decreases by one per [TurnManager.playCard] or [TurnManager.discardCard] call.
 * Board placement itself is the responsibility of [GameBoard] and must be handled separately.
 *
 * All [TurnManager] functions return a new instance rather than mutating this object.
 */
data class SaboteurGameState(
    val gameState: GameState,
    val hands: Map<String, List<TunnelCard>>,
    val drawPile: List<TunnelCard>,
    val goalCards: List<TunnelCard>,
    val startCard: TunnelCard
) {
    companion object {
        fun from(distribution: CardDistributionResult, gameState: GameState): SaboteurGameState =
            SaboteurGameState(
                gameState = gameState,
                hands = distribution.hands,
                drawPile = distribution.drawPile,
                goalCards = distribution.goalCards,
                startCard = distribution.startCard
            )
    }
}