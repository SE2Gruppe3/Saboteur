package com.aau.server

import com.aau.saboteur.model.TunnelCard
import com.aau.shared.game.GameState

data class SaboteurGameState(
    val gameState: GameState,
    val hands: Map<String, MutableList<TunnelCard>>,
    val drawPile: MutableList<TunnelCard>,
    val goalCards: List<TunnelCard>,
    val startCard: TunnelCard
) {
    companion object {
        fun from(distribution: CardDistributionResult, gameState: GameState): SaboteurGameState =
            SaboteurGameState(
                gameState = gameState,
                hands = distribution.hands.mapValues { (_, cards) -> cards.toMutableList() },
                drawPile = distribution.drawPile.toMutableList(),
                goalCards = distribution.goalCards,
                startCard = distribution.startCard
            )
    }
}