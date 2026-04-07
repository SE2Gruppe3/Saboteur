package com.aau.server

import com.aau.saboteur.model.TunnelCard

object TurnManager {

    /**
     * Plays a card from the player's hand. Automatically draws a replacement card
     * from the draw pile if available, then advances to the next player.
     */
    fun playCard(state: SaboteurGameState, playerId: String, cardId: String): SaboteurGameState {
        requireCurrentPlayer(state, playerId)
        requireCardInHand(state, playerId, cardId)

        val handWithoutCard = state.hands.getValue(playerId).filter { it.id != cardId }
        val newHand = if (state.drawPile.isNotEmpty()) handWithoutCard + state.drawPile.first() else handWithoutCard
        val newDrawPile = if (state.drawPile.isNotEmpty()) state.drawPile.drop(1) else emptyList()

        return nextPlayer(state.withHand(playerId, newHand, newDrawPile))
    }

    /**
     * Discards a card from the player's hand without placing it. Automatically draws
     * a replacement card from the draw pile if available, then advances to the next player.
     */
    fun discardCard(state: SaboteurGameState, playerId: String, cardId: String): SaboteurGameState {
        requireCurrentPlayer(state, playerId)
        requireCardInHand(state, playerId, cardId)

        val handWithoutCard = state.hands.getValue(playerId).filter { it.id != cardId }
        val newHand = if (state.drawPile.isNotEmpty()) handWithoutCard + state.drawPile.first() else handWithoutCard
        val newDrawPile = if (state.drawPile.isNotEmpty()) state.drawPile.drop(1) else emptyList()

        return nextPlayer(state.withHand(playerId, newHand, newDrawPile))
    }

    /**
     * Draws the top card from the draw pile into the player's hand, then advances to the next player.
     *
     * @throws IllegalStateException if the draw pile is empty
     */
    fun drawCard(state: SaboteurGameState, playerId: String): SaboteurGameState {
        requireCurrentPlayer(state, playerId)
        check(state.drawPile.isNotEmpty()) { "Draw pile is empty" }

        val newHand = state.hands.getValue(playerId) + state.drawPile.first()
        val newDrawPile = state.drawPile.drop(1)

        return nextPlayer(state.withHand(playerId, newHand, newDrawPile))
    }

    /**
     * Advances the turn to the next player by turn order, wrapping around after the last player.
     */
    fun nextPlayer(state: SaboteurGameState): SaboteurGameState {
        val sortedPlayers = state.gameState.players.sortedBy { it.turnOrder }
        val currentIndex = sortedPlayers.indexOfFirst { it.playerId == state.gameState.currentPlayerId }
        val nextIndex = (currentIndex + 1) % sortedPlayers.size
        val newGameState = state.gameState.copy(currentPlayerId = sortedPlayers[nextIndex].playerId)
        return state.copy(gameState = newGameState)
    }

    // --- Private helpers ---

    private fun requireCurrentPlayer(state: SaboteurGameState, playerId: String) {
        require(playerId == state.gameState.currentPlayerId) {
            "It is not $playerId's turn (current player: ${state.gameState.currentPlayerId})"
        }
    }

    private fun requireCardInHand(state: SaboteurGameState, playerId: String, cardId: String) {
        val hand = state.hands.getValue(playerId)
        require(hand.any { it.id == cardId }) {
            "Card $cardId not in hand of player $playerId"
        }
    }

    /**
     * Returns a new [SaboteurGameState] with the given player's hand and draw pile replaced.
     * All other players' hands are deep-copied to prevent shared mutable references.
     */
    private fun SaboteurGameState.withHand(
        playerId: String,
        newHand: List<TunnelCard>,
        newDrawPile: List<TunnelCard>
    ): SaboteurGameState {
        val newHands = hands.mapValues { (pid, cards) ->
            if (pid == playerId) newHand.toMutableList() else cards.toMutableList()
        }
        return copy(hands = newHands, drawPile = newDrawPile.toMutableList())
    }
}