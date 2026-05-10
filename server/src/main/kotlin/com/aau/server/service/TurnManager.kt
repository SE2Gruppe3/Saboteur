package com.aau.server.service

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.PlacedTunnelCard
import com.aau.saboteur.model.TunnelCard
import com.aau.server.model.CardDistributionResult
import com.aau.server.model.TurnResult
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference

@Service
class TurnManager {

    private val lock = Any()

    // Guarded by lock
    private var hands: Map<String, MutableList<TunnelCard>> = emptyMap()
    private var drawPile: MutableList<TunnelCard> = mutableListOf()

    private val gameState = AtomicReference(GameState())

    fun initializeGame(distribution: CardDistributionResult, initialGameState: GameState) {
        synchronized(lock) {
            hands = distribution.hands.mapValues { (_, cards) -> cards.toMutableList() }
            drawPile = distribution.drawPile.toMutableList()
        }
        gameState.set(initialGameState)
    }

    /**
     * Places a card from the player's hand onto the board, then draws a replacement and advances the turn.
     */
    fun playCard(
        playerId: String,
        cardId: String,
        position: BoardPosition,
        isRotated: Boolean
    ): TurnResult = synchronized(lock) {
        val state = gameState.get()

        require(state.currentPlayerId == playerId) {
            "It is not player $playerId's turn"
        }

        val playerHand = hands[playerId]
            ?: throw IllegalArgumentException("Player $playerId not found")
        val card = playerHand.find { it.id == cardId }
            ?: throw IllegalArgumentException("Card $cardId not in hand of player $playerId")

        val effectiveCard = if (isRotated) card.flipConnections() else card

        require(canPlaceOnBoard(position, effectiveCard, state.boardPlacements)) {
            "Card $cardId cannot be placed at position $position"
        }

        playerHand.remove(card)
        drawCardForPlayer(playerId)

        val newPlacements = state.boardPlacements + PlacedTunnelCard(position, effectiveCard)
        val newState = state.copy(
            boardPlacements = newPlacements,
            currentPlayerId = nextPlayerId(state)
        )
        gameState.set(newState)

        TurnResult(newState, hands.mapValues { it.value.toList() })
    }

    /**
     * Discards a card without placing it, draws a replacement and advances the turn.
     */
    fun discardCard(playerId: String, cardId: String): TurnResult = synchronized(lock) {
        val state = gameState.get()

        require(state.currentPlayerId == playerId) {
            "It is not player $playerId's turn"
        }

        val playerHand = hands[playerId]
            ?: throw IllegalArgumentException("Player $playerId not found")

        require(playerHand.any { it.id == cardId }) {
            "Card $cardId not in hand of player $playerId"
        }

        playerHand.removeIf { it.id == cardId }
        drawCardForPlayer(playerId)

        val newState = state.copy(currentPlayerId = nextPlayerId(state))
        gameState.set(newState)

        TurnResult(newState, hands.mapValues { it.value.toList() })
    }

    fun getGameState(): GameState = gameState.get()

    fun getHands(): Map<String, List<TunnelCard>> = synchronized(lock) {
        hands.mapValues { it.value.toList() }
    }

    // Must be called while holding lock
    private fun drawCardForPlayer(playerId: String) {
        if (drawPile.isEmpty()) return
        hands[playerId]?.add(drawPile.removeFirst())
    }

    private fun nextPlayerId(state: GameState): String? {
        val sorted = state.players.sortedBy { it.turnOrder }
        val idx = sorted.indexOfFirst { it.playerId == state.currentPlayerId }
        if (idx == -1) return sorted.firstOrNull()?.playerId
        return sorted[(idx + 1) % sorted.size].playerId
    }

    private fun canPlaceOnBoard(
        position: BoardPosition,
        card: TunnelCard,
        placements: List<PlacedTunnelCard>
    ): Boolean {
        if (placements.any { it.position == position }) return false

        val grid = placements.associateBy { it.position }
        val neighbors = mapOf(
            Direction.TOP    to grid[BoardPosition(position.row - 1, position.column)],
            Direction.BOTTOM to grid[BoardPosition(position.row + 1, position.column)],
            Direction.LEFT   to grid[BoardPosition(position.row, position.column - 1)],
            Direction.RIGHT  to grid[BoardPosition(position.row, position.column + 1)]
        )

        if (neighbors.values.none { it != null }) return false

        return neighbors.all { (dir, neighbor) ->
            if (neighbor == null) true
            else {
                val opposite = opposite(dir)
                val cardConnects = dir in card.connections
                val neighborConnects = opposite in neighbor.card.connections
                cardConnects == neighborConnects
            }
        }
    }

    private fun TunnelCard.flipConnections(): TunnelCard = copy(
        connections = connections.map {
            when (it) {
                Direction.TOP    -> Direction.BOTTOM
                Direction.BOTTOM -> Direction.TOP
                Direction.LEFT   -> Direction.RIGHT
                Direction.RIGHT  -> Direction.LEFT
            }
        }.toSet(),
        isRotated = true
    )

    private fun opposite(direction: Direction): Direction = when (direction) {
        Direction.TOP    -> Direction.BOTTOM
        Direction.BOTTOM -> Direction.TOP
        Direction.LEFT   -> Direction.RIGHT
        Direction.RIGHT  -> Direction.LEFT
    }
}