package com.aau.server.service

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.MapResult
import com.aau.saboteur.model.PlacedTunnelCard
import com.aau.saboteur.model.ToolType
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.model.blockedTool
import com.aau.saboteur.model.isBlockCard
import com.aau.saboteur.model.isGoalCardType
import com.aau.saboteur.model.isMapCard
import com.aau.saboteur.model.isPathCardType
import com.aau.saboteur.model.isRepairCard
import com.aau.saboteur.model.isRockfallCard
import com.aau.saboteur.model.repairableTools
import com.aau.server.game.*
import com.aau.server.model.CardDistributionResult
import com.aau.server.model.TurnResult
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference

@Service
class TurnManager {

    companion object {
        const val BOARD_ROWS = 9
        const val BOARD_COLUMNS = 13
    }

    private val lock = Any()

    // Guarded by lock
    private var hands: Map<String, MutableList<TunnelCard>> = emptyMap()
    private var drawPile: MutableList<TunnelCard> = mutableListOf()

    // Guarded by lock
    private var discardPile: MutableList<TunnelCard> = mutableListOf()

    // Guarded by lock
    private var knownGoalsByPlayer: MutableMap<String, MutableMap<BoardPosition, TunnelCard>> = mutableMapOf()

    // Guarded by lock: set permanently once the draw pile runs dry
    private var deckWasEmptied = false

    // Guarded by lock: consecutive player turns without a tunnel card placed, counted only after deck emptied
    private var passedSinceEmpty = 0

    private val gameState = AtomicReference(GameState())

    fun initializeGame(distribution: CardDistributionResult, initialGameState: GameState) {
        synchronized(lock) {
            hands = distribution.hands.mapValues { (_, cards) -> cards.toMutableList() }
            drawPile = distribution.drawPile.toMutableList()
            discardPile = mutableListOf()
            knownGoalsByPlayer = initialGameState.players.associate { player ->
                player.playerId to mutableMapOf<BoardPosition, TunnelCard>()
            }.toMutableMap()
            deckWasEmptied = false
            passedSinceEmpty = 0
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
            "Du bist nicht am Zug."
        }

        val currentPlayer = state.players.find { it.playerId == playerId }
            ?: throw IllegalArgumentException("Spieler $playerId nicht gefunden.")

        require(currentPlayer.blockedTools.isEmpty()) {
            "Geblockte Spieler können keine Tunnelkarten spielen."
        }

        val playerHand = hands[playerId]
            ?: throw IllegalArgumentException("Spieler $playerId nicht gefunden.")
        val card = playerHand.find { it.id == cardId }
            ?: throw IllegalArgumentException("Karte $cardId nicht in der Hand von Spieler $playerId.")

        require(card.type == CardType.PATH || card.type == CardType.DEAD_END) {
            "Diese Karte kann hier nicht platziert werden."
        }

        val effectiveCard = if (isRotated) card.rotated180() else card

        require(canPlaceOnBoard(position, effectiveCard, state.boardPlacements)) {
            "Diese Karte kann hier nicht platziert werden."
        }

        playerHand.remove(card)
        drawCardForPlayer(playerId)
        // A tunnel card was placed — reset the saboteur pass-counter regardless of deck state.
        passedSinceEmpty = 0

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

        val winner = if (isGoalReached(buildGrid(newPlacements))) "DWARVES" else null
        TurnResult(newState, hands.mapValues { it.value.toList() }, winner)
    }

    /**
     * Discards a card without placing it, draws a replacement and advances the turn.
     */
    fun discardCard(playerId: String, cardId: String): TurnResult = synchronized(lock) {
        val state = gameState.get()

        require(state.currentPlayerId == playerId) {
            "Du bist nicht am Zug."
        }

        val playerHand = hands[playerId]
            ?: throw IllegalArgumentException("Spieler $playerId nicht gefunden.")

        val discardedCard = playerHand.find { it.id == cardId }
            ?: throw IllegalArgumentException("Karte $cardId nicht in der Hand von Spieler $playerId.")

        playerHand.remove(discardedCard)
        discard(discardedCard)
        drawCardForPlayer(playerId)

        // Count this turn as a "no tunnel placed" turn if the deck is (or has become) empty.
        if (deckWasEmptied) passedSinceEmpty++

        val newState = state.copy(currentPlayerId = nextPlayerId(state))
        gameState.set(newState)

        val winner = if (deckWasEmptied && passedSinceEmpty >= state.players.size) "SABOTEURS" else null
        TurnResult(newState, hands.mapValues { it.value.toList() }, winner)
    }

    /**
     * Plays a blocking card on a target player, draws a replacement and advances the turn.
     */
    fun playBlockCard(
        playerId: String,
        cardId: String,
        targetPlayerId: String
    ): TurnResult = synchronized(lock) {
        val state = gameState.get()

        require(state.currentPlayerId == playerId) {
            "Du bist nicht am Zug."
        }

        val playerHand = hands[playerId]
            ?: throw IllegalArgumentException("Spieler $playerId nicht gefunden.")
        val card = playerHand.find { it.id == cardId }
            ?: throw IllegalArgumentException("Karte $cardId nicht in der Hand von Spieler $playerId.")

        require(card.type.isBlockCard()) {
            "Diese Karte ist keine Sperrkarte."
        }

        val toolToBlock = card.type.blockedTool()
            ?: throw IllegalArgumentException("Sperrkarte ohne zugehöriges Werkzeug.")

        val targetPlayer = state.players.find { it.playerId == targetPlayerId }
            ?: throw IllegalArgumentException("Zielspieler $targetPlayerId nicht gefunden.")

        require(toolToBlock !in targetPlayer.blockedTools) {
            "Dieses Werkzeug ist beim Zielspieler bereits blockiert."
        }

        playerHand.remove(card)
        setBlockedTools(targetPlayerId, targetPlayer.blockedTools + toolToBlock)
        discard(card)
        drawCardForPlayer(playerId)

        val updatedState = gameState.get()
        val newState = updatedState.copy(currentPlayerId = nextPlayerId(updatedState))
        gameState.set(newState)

        TurnResult(newState, hands.mapValues { it.value.toList() })
    }

    /**
     * Plays a repair card on a target player, draws a replacement and advances the turn.
     */
    fun playRepairCard(
        playerId: String,
        cardId: String,
        targetPlayerId: String,
        tool: ToolType
    ): TurnResult = synchronized(lock) {
        val state = gameState.get()

        require(state.currentPlayerId == playerId) {
            "Du bist nicht am Zug."
        }

        val playerHand = hands[playerId]
            ?: throw IllegalArgumentException("Spieler $playerId nicht gefunden.")
        val card = playerHand.find { it.id == cardId }
            ?: throw IllegalArgumentException("Karte $cardId nicht in der Hand von Spieler $playerId.")

        require(card.type.isRepairCard()) {
            "Diese Karte ist keine Reparaturkarte."
        }

        require(tool in card.type.repairableTools()) {
            "Diese Reparaturkarte kann das gewählte Werkzeug nicht reparieren."
        }

        val targetPlayer = state.players.find { it.playerId == targetPlayerId }
            ?: throw IllegalArgumentException("Zielspieler $targetPlayerId nicht gefunden.")

        require(tool in targetPlayer.blockedTools) {
            "Dieses Werkzeug ist beim Zielspieler nicht blockiert."
        }

        playerHand.remove(card)
        setBlockedTools(targetPlayerId, targetPlayer.blockedTools - tool)
        discard(card)
        drawCardForPlayer(playerId)

        val updatedState = gameState.get()
        val newState = updatedState.copy(currentPlayerId = nextPlayerId(updatedState))
        gameState.set(newState)

        TurnResult(newState, hands.mapValues { it.value.toList() })
    }

    /**
     * Plays a map card, reveals a goal card privately, draws a replacement and advances the turn.
     */
    fun playMapCard(
        playerId: String,
        cardId: String,
        targetPosition: BoardPosition
    ): Pair<TurnResult, MapResult> = synchronized(lock) {
        val state = gameState.get()

        require(state.currentPlayerId == playerId) {
            "Du bist nicht am Zug."
        }

        val playerHand = hands[playerId]
            ?: throw IllegalArgumentException("Spieler $playerId nicht gefunden.")
        val card = playerHand.find { it.id == cardId }
            ?: throw IllegalArgumentException("Karte $cardId nicht in der Hand von Spieler $playerId.")

        require(card.type.isMapCard()) {
            "Diese Karte ist keine Kartenkarte."
        }

        val targetPlacement = state.boardPlacements.find { it.position == targetPosition }
            ?: throw IllegalArgumentException("An der gewählten Position liegt keine Karte.")

        require(targetPlacement.card.type.isGoalCardType()) {
            "Die Map-Karte darf nur auf Zielkarten verwendet werden."
        }

        playerHand.remove(card)
        knownGoalsByPlayer.getOrPut(playerId) { mutableMapOf() }[targetPosition] = targetPlacement.card
        discard(card)
        drawCardForPlayer(playerId)

        val newState = state.copy(currentPlayerId = nextPlayerId(state))
        gameState.set(newState)

        val turnResult = TurnResult(newState, hands.mapValues { it.value.toList() })
        val mapResult = MapResult(
            position = targetPosition,
            card = targetPlacement.card
        )

        Pair(turnResult, mapResult)
    }

    /**
     * Plays a rockfall card, removes a path card, draws a replacement and advances the turn.
     */
    fun playRockfallCard(
        playerId: String,
        cardId: String,
        targetPosition: BoardPosition
    ): TurnResult = synchronized(lock) {
        val state = gameState.get()

        require(state.currentPlayerId == playerId) {
            "Du bist nicht am Zug."
        }

        val playerHand = hands[playerId]
            ?: throw IllegalArgumentException("Spieler $playerId nicht gefunden.")
        val card = playerHand.find { it.id == cardId }
            ?: throw IllegalArgumentException("Karte $cardId nicht in der Hand von Spieler $playerId.")

        require(card.type.isRockfallCard()) {
            "Diese Karte ist kein Felssturz."
        }

        val targetPlacement = state.boardPlacements.find { it.position == targetPosition }
            ?: throw IllegalArgumentException("An der gewählten Position liegt keine Karte.")

        require(targetPlacement.card.type.isPathCardType()) {
            "Felssturz darf nur normale Tunnelkarten entfernen."
        }

        playerHand.remove(card)
        removeBoardCard(targetPosition)
        discard(targetPlacement.card)
        discard(card)
        drawCardForPlayer(playerId)

        val updatedState = gameState.get()
        val newState = updatedState.copy(currentPlayerId = nextPlayerId(updatedState))
        gameState.set(newState)

        TurnResult(newState, hands.mapValues { it.value.toList() })
    }

    fun getGameState(): GameState = gameState.get()

    fun getHands(): Map<String, List<TunnelCard>> = synchronized(lock) {
        hands.mapValues { it.value.toList() }
    }

    fun getDiscardPile(): List<TunnelCard> = synchronized(lock) {
        discardPile.toList()
    }

    fun getKnownGoalsForPlayer(playerId: String): Map<BoardPosition, TunnelCard> = synchronized(lock) {
        knownGoalsByPlayer[playerId]?.toMap().orEmpty()
    }

    /**
     * Returns all board positions where the given card can legally be placed.
     * Checks both placement validity and reachability from start.
     *
     * @param card the tunnel card to place
     * @param isRotated whether the card is rotated 180°
     * @param placements the current board state
     * @return list of valid positions
     */
    fun getValidPositions(
        card: TunnelCard,
        isRotated: Boolean,
        placements: List<PlacedTunnelCard>
    ): List<BoardPosition> {
        val effectiveCard = if (isRotated) card.rotated180() else card
        val occupiedPositions = placements.map { it.position }.toSet()

        val candidates = mutableSetOf<BoardPosition>()
        for (pos in occupiedPositions) {
            for (dir in Direction.values()) {
                val neighbor = boardNeighbor(pos, dir)
                if (neighbor !in occupiedPositions &&
                    neighbor.row in 0 until BOARD_ROWS &&
                    neighbor.column in 0 until BOARD_COLUMNS
                ) {
                    candidates.add(neighbor)
                }
            }
        }

        return candidates.filter { pos -> canPlaceOnBoard(pos, effectiveCard, placements) }
    }

    // Must be called while holding lock
    private fun drawCardForPlayer(playerId: String) {
        if (drawPile.isEmpty()) {
            deckWasEmptied = true
            return
        }
        hands[playerId]?.add(drawPile.removeFirst())
        if (drawPile.isEmpty()) deckWasEmptied = true
    }

    // Must be called while holding lock
    private fun discard(card: TunnelCard) {
        discardPile.add(card)
    }

    // Must be called while holding lock
    private fun setBlockedTools(playerId: String, blockedTools: Set<ToolType>) {
        val state = gameState.get()
        val updatedPlayers = state.players.map { playerTurn ->
            if (playerTurn.playerId == playerId) {
                playerTurn.copy(blockedTools = blockedTools)
            } else {
                playerTurn
            }
        }
        gameState.set(state.copy(players = updatedPlayers))
    }

    // Must be called while holding lock
    private fun removeBoardCard(targetPosition: BoardPosition) {
        val state = gameState.get()
        val updatedPlacements = state.boardPlacements.filterNot { it.position == targetPosition }
        gameState.set(state.copy(boardPlacements = updatedPlacements))
    }

    private fun nextPlayerId(state: GameState): String? {
        val sorted = state.players.sortedBy { it.turnOrder }
        val idx = sorted.indexOfFirst { it.playerId == state.currentPlayerId }
        if (idx == -1) return sorted.firstOrNull()?.playerId
        return sorted[(idx + 1) % sorted.size].playerId
    }

    private fun buildGrid(placements: List<PlacedTunnelCard>): Map<BoardPosition, PlacedTunnelCard> =
        placements.associateBy { it.position }

    private fun canPlaceOnBoard(
        position: BoardPosition,
        card: TunnelCard,
        placements: List<PlacedTunnelCard>
    ): Boolean {
        if (placements.any { it.position == position }) return false

        val grid = buildGrid(placements)
        val neighbors = mapOf(
            Direction.TOP to grid[BoardPosition(position.row - 1, position.column)],
            Direction.BOTTOM to grid[BoardPosition(position.row + 1, position.column)],
            Direction.LEFT to grid[BoardPosition(position.row, position.column - 1)],
            Direction.RIGHT to grid[BoardPosition(position.row, position.column + 1)]
        )

        if (neighbors.values.none { it != null }) return false

        // XOR rule: invalid if exactly one side connects (open tunnel). Both connect or both wall → valid.
        val adjacencyOk = neighbors.all { (dir, neighbor) ->
            if (neighbor == null) true
            // Unrevealed goal cards are exempt from adjacency matching here —
            // their connections will be corrected by auto-rotation at reveal time.
            else if (neighbor.card.type == CardType.GOAL && !neighbor.card.isRevealed) true
            else {
                val cardConnects = dir in card.connections
                val neighborConnects = opposite(dir) in neighbor.card.connections
                cardConnects == neighborConnects
            }
        }
        if (!adjacencyOk) return false

        return isReachableFromStart(position, grid)
    }

    // BFS from the START card. PATH, START, and revealed GOAL cards are traversable nodes;
    // unrevealed goals and dead-ends are not.
    // Returns true if any directly adjacent reachable card connects toward the target position.
    private fun isReachableFromStart(
        position: BoardPosition,
        grid: Map<BoardPosition, PlacedTunnelCard>
    ): Boolean {
        val visited = bfsVisited(grid)
        // The XOR adjacency rule (checked before this call) guarantees that if a visited neighbor
        // connects toward `position` (neighborConnects=true), the card being placed also connects
        // back toward that neighbor (cardConnects=true). No need to re-check the card's connections here.
        return Direction.values().any { dir ->
            val neighborPos = boardNeighbor(position, dir)
            neighborPos in visited && opposite(dir) in (grid[neighborPos]?.card?.connections ?: emptySet())
        }
    }

    /**
     * Checks whether the gold goal card is reachable from the start card via a connected tunnel path.
     * A goal card counts as reached if it is revealed, has subtype goal_gold (isGoal == true),
     * and is connected to the start position through valid tunnel connections.
     *
     * @param grid the current board state
     * @return true if the gold goal is reachable from start, false otherwise
     */
    private fun isGoalReached(grid: Map<BoardPosition, PlacedTunnelCard>): Boolean {
        val goldGoalPositions = grid.values
            .filter { it.card.type == CardType.GOAL && it.card.isRevealed && it.card.isGoal }
            .map { it.position }

        if (goldGoalPositions.isEmpty()) return false

        val visited = bfsVisited(grid)
        return goldGoalPositions.any { it in visited }
    }

    private fun bfsVisited(grid: Map<BoardPosition, PlacedTunnelCard>): Set<BoardPosition> {
        val startPos = grid.entries.find { it.value.card.type == CardType.START }?.key ?: return emptySet()
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

        return visited
    }

    private fun boardNeighbor(pos: BoardPosition, dir: Direction): BoardPosition = when (dir) {
        Direction.TOP -> BoardPosition(pos.row - 1, pos.column)
        Direction.BOTTOM -> BoardPosition(pos.row + 1, pos.column)
        Direction.LEFT -> BoardPosition(pos.row, pos.column - 1)
        Direction.RIGHT -> BoardPosition(pos.row, pos.column + 1)
    }

    private fun revealGoalCards(
        placedPosition: BoardPosition,
        placedCard: TunnelCard,
        placements: List<PlacedTunnelCard>
    ): List<PlacedTunnelCard> {
        // Build a mutable lookup for O(1) updates during the reveal scan.
        // Known design gap: if a goal card already has a PATH neighbor on side A (placed under the
        // unrevealed-goal exemption) and is then auto-rotated 180° on reveal from side B, the
        // previously-placed neighbor's connection on side A may no longer match the rotated goal.
        // By game rules, goal cards are placed far from the start and reachable from only one
        // direction at reveal time, so this conflict cannot occur in a normal game. The exemption
        // is therefore intentional and any mismatch would indicate an invalid board state.
        val grid = placements.associateBy { it.position }.toMutableMap()

        for (dir in Direction.values()) {
            val neighborPos = boardNeighbor(placedPosition, dir)
            val neighborPlacement = grid[neighborPos] ?: continue
            val neighborCard = neighborPlacement.card

            if (neighborCard.type != CardType.GOAL || neighborCard.isRevealed) continue
            if (dir !in placedCard.connections) continue

            val goalConnects = opposite(dir) in neighborCard.connections
            val revealedCard = if (!goalConnects) {
                neighborCard.copy(
                    connections = neighborCard.connections.map { opposite(it) }.toSet(),
                    isRotated = true,
                    isRevealed = true
                )
            } else {
                neighborCard.copy(isRevealed = true)
            }

            grid[neighborPos] = neighborPlacement.copy(card = revealedCard)
        }

        // Preserve original list order by mapping through the updated grid rather than
        // relying on LinkedHashMap insertion-order from grid.values.
        // grid was built from placements via associateBy, so every position is guaranteed present.
        return placements.map { grid.getValue(it.position) }
    }

    private fun opposite(direction: Direction): Direction = when (direction) {
        Direction.TOP -> Direction.BOTTOM
        Direction.BOTTOM -> Direction.TOP
        Direction.LEFT -> Direction.RIGHT
        Direction.RIGHT -> Direction.LEFT
    }
}