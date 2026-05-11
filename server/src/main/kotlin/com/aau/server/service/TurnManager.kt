package com.aau.server.service

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CardType
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

        require(card.type == CardType.PATH || card.type == CardType.DEAD_END) {
            "Diese Karte kann hier nicht platziert werden."
        }

        val effectiveCard = if (isRotated) card.flipConnections() else card

        require(canPlaceOnBoard(position, effectiveCard, state.boardPlacements)) {
            "Diese Karte kann hier nicht platziert werden."
        }

        playerHand.remove(card)
        drawCardForPlayer(playerId)

        val placementsWithCard = state.boardPlacements + PlacedTunnelCard(position, effectiveCard)
        val newPlacements = if (effectiveCard.type == CardType.PATH) {
            revealGoalCards(position, effectiveCard, placementsWithCard)
        } else {
            placementsWithCard
        }
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

        // XOR rule: invalid if exactly one side connects (open tunnel). Both connect or both wall → valid.
        // Unrevealed goal cards are exempt: they will be revealed and auto-rotated after placement.
        val adjacencyOk = neighbors.all { (dir, neighbor) ->
            if (neighbor == null) true
            else if (neighbor.card.type == CardType.GOAL && !neighbor.card.isRevealed) true
            else {
                val cardConnects = dir in card.connections
                val neighborConnects = opposite(dir) in neighbor.card.connections
                cardConnects == neighborConnects
            }
        }
        if (!adjacencyOk) return false

        return isReachableFromStart(position, placements)
    }

    // BFS from the START card. PATH, START, and revealed GOAL cards are traversable nodes;
    // unrevealed goals and dead-ends are not.
    // Returns true if any directly adjacent reachable card connects toward the target position.
    private fun isReachableFromStart(
        position: BoardPosition,
        placements: List<PlacedTunnelCard>
    ): Boolean {
        val grid = placements.associateBy { it.position }
        val startPos = grid.entries.find { it.value.card.type == CardType.START }?.key ?: return false

        val visited = mutableSetOf<BoardPosition>()
        val queue = ArrayDeque<BoardPosition>()
        queue.add(startPos)
        visited.add(startPos)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentCard = grid[current]?.card ?: continue
            for (dir in Direction.values()) {
                val neighborPos = boardNeighbor(current, dir)
                if (neighborPos in visited) continue
                val neighborCard = grid[neighborPos]?.card ?: continue
                val traversable = neighborCard.type == CardType.PATH ||
                    neighborCard.type == CardType.START ||
                    (neighborCard.type == CardType.GOAL && neighborCard.isRevealed)
                if (!traversable) continue
                if (dir in currentCard.connections && opposite(dir) in neighborCard.connections) {
                    visited.add(neighborPos)
                    queue.add(neighborPos)
                }
            }
        }

        return Direction.values().any { dir ->
            val neighborPos = boardNeighbor(position, dir)
            neighborPos in visited && opposite(dir) in (grid[neighborPos]?.card?.connections ?: emptySet())
        }
    }

    private fun boardNeighbor(pos: BoardPosition, dir: Direction): BoardPosition = when (dir) {
        Direction.TOP    -> BoardPosition(pos.row - 1, pos.column)
        Direction.BOTTOM -> BoardPosition(pos.row + 1, pos.column)
        Direction.LEFT   -> BoardPosition(pos.row, pos.column - 1)
        Direction.RIGHT  -> BoardPosition(pos.row, pos.column + 1)
    }

    private fun revealGoalCards(
        placedPosition: BoardPosition,
        placedCard: TunnelCard,
        placements: List<PlacedTunnelCard>
    ): List<PlacedTunnelCard> {
        val grid = placements.associateBy { it.position }.toMutableMap()
        for (dir in Direction.values()) {
            val neighborPos = boardNeighbor(placedPosition, dir)
            val neighborPlacement = grid[neighborPos] ?: continue
            val neighborCard = neighborPlacement.card
            if (neighborCard.type != CardType.GOAL || neighborCard.isRevealed) continue
            if (dir !in placedCard.connections) continue
            val goalConnects = opposite(dir) in neighborCard.connections
            val revealedCard = if (!goalConnects) neighborCard.copy(
                connections = neighborCard.connections.map { opposite(it) }.toSet(),
                isRotated = true,
                isRevealed = true
            ) else neighborCard.copy(isRevealed = true)
            grid[neighborPos] = neighborPlacement.copy(card = revealedCard)
        }
        return grid.values.toList()
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