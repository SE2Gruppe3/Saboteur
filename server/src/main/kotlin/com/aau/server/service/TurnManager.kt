package com.aau.server.service

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.PlacedTunnelCard
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.PlayerTurn
import com.aau.server.game.*
import com.aau.server.model.CardDistributionResult
import com.aau.server.model.TurnResult
import com.aau.server.model.GameEntity
import com.aau.server.repository.GameRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class TurnManager(
    private val gameRepository: GameRepository,
    private val objectMapper: ObjectMapper,
    private val gameService: GameService
) {

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

    @PostConstruct
    fun loadFromDb() {
        gameRepository.findAll().forEach { entity ->
            try {
                val hands: Map<String, List<TunnelCard>> = objectMapper.readValue(entity.handsJson)
                val mutableHands = hands.mapValues { it.value.toMutableList() }
                val drawPile: List<TunnelCard> = objectMapper.readValue(entity.drawPileJson)
                val discardPile: List<TunnelCard> = if (entity.discardPileJson.isNotEmpty()) objectMapper.readValue(entity.discardPileJson) else mutableListOf()
                val playersTurn: List<PlayerTurn> = objectMapper.readValue(entity.playersTurnJson)
                val board: List<PlacedTunnelCard> = objectMapper.readValue(entity.boardJson)
                val playerRoles: Map<String, Player> = objectMapper.readValue(entity.playerRolesJson)

                val gameState = GameState(
                    players = playersTurn,
                    currentPlayerId = entity.currentPlayerId,
                    boardPlacements = board
                )

                games[entity.lobbyCode] = GameInternalState(
                    hands = mutableHands,
                    drawPile = drawPile.toMutableList(),
                    discardPile = discardPile.toMutableList(),
                    deckWasEmptied = entity.deckWasEmptied,
                    passedSinceEmpty = entity.passedSinceEmpty,
                    gameState = gameState
                )
                
                gameService.setPlayerData(entity.lobbyCode, playerRoles)
            } catch (e: Exception) {
                // Log error but continue with other games
            }
        }
    }

    private fun persist(lobbyCode: String) {
        val internal = games[lobbyCode] ?: return
        val playerRoles = gameService.getAllPlayerData(lobbyCode)
        
        val entity = GameEntity(
            lobbyCode = lobbyCode,
            currentPlayerId = internal.gameState.currentPlayerId,
            boardJson = objectMapper.writeValueAsString(internal.gameState.boardPlacements),
            drawPileJson = objectMapper.writeValueAsString(internal.drawPile),
            discardPileJson = objectMapper.writeValueAsString(internal.discardPile),
            handsJson = objectMapper.writeValueAsString(internal.hands),
            playersTurnJson = objectMapper.writeValueAsString(internal.gameState.players),
            playerRolesJson = objectMapper.writeValueAsString(playerRoles),
            deckWasEmptied = internal.deckWasEmptied,
            passedSinceEmpty = internal.passedSinceEmpty
        )
        gameRepository.save(entity)
    }

    fun initializeGame(lobbyCode: String, distribution: CardDistributionResult, initialGameState: GameState) {
        val internal = GameInternalState(
            hands = distribution.hands.mapValues { (_, cards) -> cards.toMutableList() },
            drawPile = distribution.drawPile.toMutableList(),
            gameState = initialGameState
        )
        games[lobbyCode] = internal
        persist(lobbyCode)
    }

    fun playCard(
        lobbyCode: String,
        playerId: String,
        cardId: String,
        position: BoardPosition,
        isRotated: Boolean
    ): TurnResult {
        val internal = games[lobbyCode] ?: throw IllegalArgumentException("Game not found for lobby $lobbyCode")
        
        synchronized(internal) {
            val state = internal.gameState

            require(state.currentPlayerId == playerId) { "Du bist nicht am Zug." }

            val playerHand = internal.hands[playerId] ?: throw IllegalArgumentException("Hand nicht gefunden.")
            val card = playerHand.find { it.id == cardId } ?: throw IllegalArgumentException("Karte nicht in Hand.")

            require(card.type == CardType.PATH || card.type == CardType.DEAD_END) { "Ungültiger Kartentyp." }

            val effectiveCard = if (isRotated) card.rotated180() else card
            require(canPlaceOnBoard(position, effectiveCard, state.boardPlacements)) { "Platzierung nicht möglich." }

            playerHand.remove(card)
            drawCardForPlayer(internal, playerId)
            
            internal.passedSinceEmpty = 0

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
            internal.gameState = newState
            persist(lobbyCode)

            val winner = if (isGoalReached(buildGrid(newPlacements))) "DWARVES" else null
            return TurnResult(newState, internal.hands.mapValues { it.value.toList() }, winner)
        }
    }

    fun discardCard(lobbyCode: String, playerId: String, cardId: String): TurnResult {
        val internal = games[lobbyCode] ?: throw IllegalArgumentException("Game not found")

        synchronized(internal) {
            val state = internal.gameState
            require(state.currentPlayerId == playerId) { "Nicht am Zug." }

            val playerHand = internal.hands[playerId] ?: throw IllegalArgumentException("Hand nicht gefunden.")
            val card = playerHand.find { it.id == cardId } ?: throw IllegalArgumentException("Karte nicht gefunden.")

            playerHand.remove(card)
            internal.discardPile.add(card)
            drawCardForPlayer(internal, playerId)
            
            if (internal.deckWasEmptied) internal.passedSinceEmpty++

            val newState = state.copy(currentPlayerId = nextPlayerId(state))
            internal.gameState = newState
            persist(lobbyCode)

            val winner = if (internal.deckWasEmptied && internal.passedSinceEmpty >= state.players.size) "SABOTEURS" else null
            return TurnResult(newState, internal.hands.mapValues { it.value.toList() }, winner)
        }
    }

    fun getGameState(lobbyCode: String): GameState = games[lobbyCode]?.gameState ?: GameState()

    fun getHands(lobbyCode: String): Map<String, List<TunnelCard>> {
        val internal = games[lobbyCode] ?: return emptyMap()
        return synchronized(internal) {
            internal.hands.mapValues { it.value.toList() }
        }
    }

    fun getValidPositions(
        lobbyCode: String,
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
                    neighbor.column in 0 until BOARD_COLUMNS) {
                    candidates.add(neighbor)
                }
            }
        }

        return candidates.filter { pos -> canPlaceOnBoard(pos, effectiveCard, placements) }
    }

    private fun drawCardForPlayer(internal: GameInternalState, playerId: String) {
        if (internal.drawPile.isEmpty()) {
            internal.deckWasEmptied = true
            return
        }
        internal.hands[playerId]?.add(internal.drawPile.removeFirst())
        if (internal.drawPile.isEmpty()) internal.deckWasEmptied = true
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
            Direction.TOP    to grid[BoardPosition(position.row - 1, position.column)],
            Direction.BOTTOM to grid[BoardPosition(position.row + 1, position.column)],
            Direction.LEFT   to grid[BoardPosition(position.row, position.column - 1)],
            Direction.RIGHT  to grid[BoardPosition(position.row, position.column + 1)]
        )
        if (neighbors.values.none { it != null }) return false
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
        return isReachableFromStart(position, grid)
    }

    private fun isReachableFromStart(position: BoardPosition, grid: Map<BoardPosition, PlacedTunnelCard>): Boolean {
        val visited = bfsVisited(grid)
        return Direction.values().any { dir ->
            val neighborPos = boardNeighbor(position, dir)
            neighborPos in visited && opposite(dir) in (grid[neighborPos]?.card?.connections ?: emptySet())
        }
    }

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
        queue.add(startPos); visited.add(startPos)
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
        Direction.TOP    -> BoardPosition(pos.row - 1, pos.column)
        Direction.BOTTOM -> BoardPosition(pos.row + 1, pos.column)
        Direction.LEFT   -> BoardPosition(pos.row, pos.column - 1)
        Direction.RIGHT  -> BoardPosition(pos.row, pos.column + 1)
    }

    private fun revealGoalCards(placedPosition: BoardPosition, placedCard: TunnelCard, placements: List<PlacedTunnelCard>): List<PlacedTunnelCard> {
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
        return placements.map { grid.getValue(it.position) }
    }

    private fun opposite(direction: Direction): Direction = when (direction) {
        Direction.TOP    -> Direction.BOTTOM
        Direction.BOTTOM -> Direction.TOP
        Direction.LEFT   -> Direction.RIGHT
        Direction.RIGHT  -> Direction.LEFT
    }
}
