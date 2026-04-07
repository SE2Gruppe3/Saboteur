package com.aau.server

import com.aau.saboteur.model.TunnelCard
import com.aau.shared.game.GameState

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