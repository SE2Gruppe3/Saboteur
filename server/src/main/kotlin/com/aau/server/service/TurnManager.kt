package com.aau.server.service

import com.aau.saboteur.model.*
import com.aau.server.game.*
import com.aau.server.model.CardDistributionResult
import com.aau.server.model.TurnResult
import com.aau.server.model.GameEntity
import com.aau.server.repository.GameRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap

@Service
class TurnManager(
    private val gameRepository: GameRepository,
    private val objectMapper: ObjectMapper,
    private val gameService: GameService
) {
    private val logger = LoggerFactory.getLogger(TurnManager::class.java)

    companion object {
        const val BOARD_ROWS = 9
        const val BOARD_COLUMNS = 13
    }

    private data class GameInternalState(
        var hands: Map<String, MutableList<TunnelCard>>,
        var drawPile: MutableList<TunnelCard>,
        var discardPile: MutableList<TunnelCard> = mutableListOf(),
        var deckWasEmptied: Boolean = false,
        var passedSinceEmpty: Int = 0,
        var gameState: GameState
    )

    private val games = ConcurrentHashMap<String, GameInternalState>()

    @Transactional
    fun loadFromDb(): Int {
        val allEntities = gameRepository.findAll()
        allEntities.forEach { entity ->
            try {
                val hands: Map<String, List<TunnelCard>> = objectMapper.readValue(entity.handsJson)
                val drawPile: List<TunnelCard> = objectMapper.readValue(entity.drawPileJson)
                val discardPile: List<TunnelCard> = if (entity.discardPileJson.isNotEmpty()) objectMapper.readValue(entity.discardPileJson) else mutableListOf()
                val playersTurn: List<PlayerTurn> = objectMapper.readValue(entity.playersTurnJson)
                val board: List<PlacedTunnelCard> = objectMapper.readValue(entity.boardJson)
                val playerRoles: Map<String, Player> = objectMapper.readValue(entity.playerRolesJson)

                games[entity.lobbyCode] = GameInternalState(
                    hands = hands.mapValues { it.value.toMutableList() },
                    drawPile = drawPile.toMutableList(),
                    discardPile = discardPile.toMutableList(),
                    deckWasEmptied = entity.deckWasEmptied,
                    passedSinceEmpty = entity.passedSinceEmpty,
                    gameState = GameState(players = playersTurn, currentPlayerId = entity.currentPlayerId, boardPlacements = board)
                )
                gameService.setPlayerData(entity.lobbyCode, playerRoles)
            } catch (e: Exception) {
                logger.error("Failed to recover game {}: {}", entity.lobbyCode, e.message)
            }
        }
        return games.size
    }

    @Transactional
    fun playCard(lobbyCode: String, playerId: String, cardId: String, position: BoardPosition, isRotated: Boolean): TurnResult {
        val internal = games[lobbyCode] ?: throw IllegalArgumentException("Game not found")
        synchronized(internal) {
            try {
                val state = internal.gameState
                require(state.currentPlayerId == playerId) { "Not your turn" }

                val playerHand = internal.hands[playerId] ?: throw IllegalArgumentException("Hand not found")
                val card = playerHand.find { it.id == cardId } ?: throw IllegalArgumentException("Card not in hand")
                
                val effectiveCard = if (isRotated) card.rotated180() else card
                require(canPlaceOnBoard(position, effectiveCard, state.boardPlacements)) { "Invalid placement" }

                // Mutation
                playerHand.remove(card)
                drawCardForPlayer(internal, playerId)
                val placementsWithCard = state.boardPlacements + PlacedTunnelCard(position, effectiveCard)
                val newPlacements = revealGoalCards(position, effectiveCard, placementsWithCard)
                
                internal.gameState = state.copy(boardPlacements = newPlacements, currentPlayerId = nextPlayerId(state))
                
                persist(lobbyCode)
                
                val winner = if (isGoalReached(buildGrid(newPlacements))) "DWARVES" else null
                return TurnResult(internal.gameState, internal.hands.mapValues { it.value.toList() }, winner)
            } catch (e: Exception) {
                logger.error("Divergence detected in playCard for {}. Invaliding RAM cache.", lobbyCode)
                games.remove(lobbyCode)
                throw e
            }
        }
    }

    @Transactional
    fun discardCard(lobbyCode: String, playerId: String, cardId: String): TurnResult {
        val internal = games[lobbyCode] ?: throw IllegalArgumentException("Game not found")
        synchronized(internal) {
            try {
                require(internal.gameState.currentPlayerId == playerId) { "Not your turn" }
                val playerHand = internal.hands[playerId] ?: throw IllegalArgumentException("Hand not found")
                val card = playerHand.find { it.id == cardId } ?: throw IllegalArgumentException("Card not found")

                playerHand.remove(card)
                internal.discardPile.add(card)
                drawCardForPlayer(internal, playerId)
                
                if (internal.deckWasEmptied) internal.passedSinceEmpty++
                internal.gameState = internal.gameState.copy(currentPlayerId = nextPlayerId(internal.gameState))
                
                persist(lobbyCode)

                val winner = if (internal.deckWasEmptied && internal.passedSinceEmpty >= internal.gameState.players.size) "SABOTEURS" else null
                return TurnResult(internal.gameState, internal.hands.mapValues { it.value.toList() }, winner)
            } catch (e: Exception) {
                logger.error("Divergence detected in discardCard for {}. Invaliding RAM cache.", lobbyCode)
                games.remove(lobbyCode)
                throw e
            }
        }
    }

    private fun persist(lobbyCode: String) {
        val internal = games[lobbyCode] ?: return
        val entity = GameEntity(
            lobbyCode = lobbyCode,
            currentPlayerId = internal.gameState.currentPlayerId,
            boardJson = objectMapper.writeValueAsString(internal.gameState.boardPlacements),
            drawPileJson = objectMapper.writeValueAsString(internal.drawPile),
            discardPileJson = objectMapper.writeValueAsString(internal.discardPile),
            handsJson = objectMapper.writeValueAsString(internal.hands),
            playersTurnJson = objectMapper.writeValueAsString(internal.gameState.players),
            playerRolesJson = objectMapper.writeValueAsString(gameService.getAllPlayerData(lobbyCode)),
            deckWasEmptied = internal.deckWasEmptied,
            passedSinceEmpty = internal.passedSinceEmpty
        )
        gameRepository.save(entity)
    }

    fun removeGame(lobbyCode: String) {
        games.remove(lobbyCode)
    }

    @Transactional
    fun initializeGame(lobbyCode: String, distribution: CardDistributionResult, initialGameState: GameState) {
        try {
            val internal = GameInternalState(
                hands = distribution.hands.mapValues { it.value.toMutableList() },
                drawPile = distribution.drawPile.toMutableList(),
                gameState = initialGameState
            )
            games[lobbyCode] = internal
            persist(lobbyCode)
        } catch (e: Exception) {
            games.remove(lobbyCode)
            throw e
        }
    }

    fun getGameStateSnapshot(lobbyCode: String): GameState {
        val internal = games[lobbyCode] ?: throw IllegalArgumentException("Game not found")
        return synchronized(internal) { internal.gameState.copy() }
    }
    
    fun getGameState(lobbyCode: String): GameState = getGameStateSnapshot(lobbyCode)

    fun getHands(lobbyCode: String): Map<String, List<TunnelCard>> {
        val internal = games[lobbyCode] ?: return emptyMap()
        return synchronized(internal) { internal.hands.mapValues { it.value.toList() } }
    }

    fun getValidPositions(lobbyCode: String, card: TunnelCard, isRotated: Boolean, placements: List<PlacedTunnelCard>): List<BoardPosition> {
        val effectiveCard = if (isRotated) card.rotated180() else card
        val occupied = placements.map { it.position }.toSet()
        val candidates = mutableSetOf<BoardPosition>()
        for (pos in occupied) {
            for (dir in Direction.values()) {
                val neighbor = boardNeighbor(pos, dir)
                if (neighbor !in occupied && neighbor.row in 0 until BOARD_ROWS && neighbor.column in 0 until BOARD_COLUMNS) {
                    candidates.add(neighbor)
                }
            }
        }
        return candidates.filter { pos -> canPlaceOnBoard(pos, effectiveCard, placements) }
    }

    private fun drawCardForPlayer(internal: GameInternalState, playerId: String) {
        if (internal.drawPile.isEmpty()) { internal.deckWasEmptied = true; return }
        internal.hands[playerId]?.add(internal.drawPile.removeFirst())
        if (internal.drawPile.isEmpty()) internal.deckWasEmptied = true
    }

    private fun nextPlayerId(state: GameState): String? {
        val sorted = state.players.sortedBy { it.turnOrder }
        val idx = sorted.indexOfFirst { it.playerId == state.currentPlayerId }
        return if (idx == -1) sorted.firstOrNull()?.playerId else sorted[(idx + 1) % sorted.size].playerId
    }

    private fun buildGrid(placements: List<PlacedTunnelCard>) = placements.associateBy { it.position }
    private fun canPlaceOnBoard(p: BoardPosition, c: TunnelCard, pl: List<PlacedTunnelCard>): Boolean {
        if (pl.any { it.position == p }) return false
        val grid = buildGrid(pl)
        val neighbors = Direction.values().map { it to grid[boardNeighbor(p, it)] }
        if (neighbors.all { it.second == null }) return false
        return neighbors.all { (dir, n) -> 
            if (n == null || (n.card.type == CardType.GOAL && !n.card.isRevealed)) true
            else (dir in c.connections) == (opposite(dir) in n.card.connections)
        } && isReachableFromStart(p, grid)
    }

    private fun isReachableFromStart(p: BoardPosition, grid: Map<BoardPosition, PlacedTunnelCard>): Boolean {
        val visited = bfsVisited(grid)
        return Direction.values().any { dir ->
            val n = boardNeighbor(p, dir)
            n in visited && opposite(dir) in (grid[n]?.card?.connections ?: emptySet())
        }
    }

    private fun isGoalReached(grid: Map<BoardPosition, PlacedTunnelCard>): Boolean {
        val goals = grid.values.filter { it.card.type == CardType.GOAL && it.card.isRevealed && it.card.isGoal }
        if (goals.isEmpty()) return false
        val visited = bfsVisited(grid)
        return goals.any { it.position in visited }
    }

    private fun bfsVisited(grid: Map<BoardPosition, PlacedTunnelCard>): Set<BoardPosition> {
        val startPos = grid.entries.find { it.value.card.type == CardType.START }?.key ?: return emptySet()
        val visited = mutableSetOf(startPos)
        val queue = ArrayDeque(listOf(startPos))
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            val currCard = grid[curr]?.card ?: continue
            for (dir in Direction.values()) {
                val next = boardNeighbor(curr, dir)
                if (next in visited) continue
                val nextCard = grid[next]?.card ?: continue
                if (!(nextCard.type == CardType.PATH || nextCard.type == CardType.START || (nextCard.type == CardType.GOAL && nextCard.isRevealed))) continue
                if (dir in currCard.connections && opposite(dir) in nextCard.connections) {
                    visited.add(next); queue.add(next)
                }
            }
        }
        return visited
    }

    private fun boardNeighbor(pos: BoardPosition, dir: Direction) = when (dir) {
        Direction.TOP -> BoardPosition(pos.row - 1, pos.column)
        Direction.BOTTOM -> BoardPosition(pos.row + 1, pos.column)
        Direction.LEFT -> BoardPosition(pos.row, pos.column - 1)
        Direction.RIGHT -> BoardPosition(pos.row, pos.column + 1)
    }

    private fun revealGoalCards(p: BoardPosition, c: TunnelCard, pl: List<PlacedTunnelCard>): List<PlacedTunnelCard> {
        val grid = pl.associateBy { it.position }.toMutableMap()
        for (dir in Direction.values()) {
            val nPos = boardNeighbor(p, dir)
            val nPl = grid[nPos] ?: continue
            if (nPl.card.type != CardType.GOAL || nPl.card.isRevealed || dir !in c.connections) continue
            val goalConnects = opposite(dir) in nPl.card.connections
            grid[nPos] = nPl.copy(card = if (!goalConnects) nPl.card.copy(connections = nPl.card.connections.map { opposite(it) }.toSet(), isRotated = true, isRevealed = true) else nPl.card.copy(isRevealed = true))
        }
        return pl.map { grid.getValue(it.position) }
    }

    private fun opposite(d: Direction) = when (d) {
        Direction.TOP -> Direction.BOTTOM
        Direction.BOTTOM -> Direction.TOP
        Direction.LEFT -> Direction.RIGHT
        Direction.RIGHT -> Direction.LEFT
    }
}
