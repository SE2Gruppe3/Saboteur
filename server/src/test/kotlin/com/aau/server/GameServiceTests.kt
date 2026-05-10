package com.aau.server

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.PlacedTunnelCard
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.ToolType
import com.aau.saboteur.model.TunnelCard
import com.aau.server.service.GameService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GameServiceTests {

    private lateinit var gameService: GameService

    @BeforeEach
    fun setup() {
        gameService = GameService()
    }

    private fun createPlayers(): List<Player> = listOf(
        Player("1", "Alice"),
        Player("2", "Bob"),
        Player("3", "Charlie")
    )

    @Test
    fun `initial state is empty`() {
        val state = gameService.getGameState()
        assertTrue(state.players.isEmpty())
        assertNull(state.currentPlayerId)
    }

    @Test
    fun `startGame initializes everything correctly`() {
        val players = createPlayers()

        val result = gameService.startGame(players)

        val state = result.gameState
        assertEquals(3, state.players.size)

        val turnOrders = state.players.map { it.turnOrder }.sorted()
        assertEquals(listOf(1, 2, 3), turnOrders)

        val playerIds = state.players.map { it.playerId }.toSet()
        assertEquals(setOf("1", "2", "3"), playerIds)

        assertNotNull(state.currentPlayerId)
        assertTrue(playerIds.contains(state.currentPlayerId))

        val firstPlayer = state.players.minBy { it.turnOrder }
        assertEquals(firstPlayer.playerId, state.currentPlayerId)

        val roleData = result.playerRoles
        assertEquals(3, roleData.size)
        assertNotNull(roleData["1"]?.role)
        assertNotNull(roleData["2"]?.role)
        assertNotNull(roleData["3"]?.role)

        val player1 = gameService.getPlayer("1")
        assertNotNull(player1)
        assertEquals(roleData["1"]?.role, player1?.role)

        assertNull(gameService.getPlayer("999"))

        val cardDist = result.cardDistribution
        assertEquals(3, cardDist.hands.size)
        cardDist.hands.values.forEach { hand ->
            assertEquals(6, hand.size)
        }
    }

    @Test
    fun `startGame handles invalid player count`() {
        assertThrows(IllegalArgumentException::class.java) {
            gameService.startGame(listOf(Player("1", "A"), Player("2", "B")))
        }

        val tooMany = (1..11).map { Player(it.toString(), "P$it") }
        assertThrows(IllegalArgumentException::class.java) {
            gameService.startGame(tooMany)
        }
    }

    @Test
    fun `playBlockCard blocks target tool discards card and advances turn`() {
        gameService.startGame(createPlayers())

        val currentPlayerId = gameService.getGameState().currentPlayerId!!
        val targetPlayerId = gameService.getGameState()
            .players
            .first { it.playerId != currentPlayerId }
            .playerId

        val blockCard = TunnelCard(
            id = "block-1",
            type = CardType.PICKAXE_RED,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(blockCard))

        gameService.playBlockCard(
            playerId = currentPlayerId,
            cardId = blockCard.id,
            targetPlayerId = targetPlayerId
        )

        val updatedTarget = gameService.getGameState().players.first { it.playerId == targetPlayerId }
        assertTrue(updatedTarget.blockedTools.contains(ToolType.PICKAXE))

        assertTrue(gameService.getDiscardPile().any { it.id == blockCard.id })
        assertFalse(gameService.getHandForPlayer(currentPlayerId).any { it.id == blockCard.id })
        assertNotEquals(currentPlayerId, gameService.getGameState().currentPlayerId)
    }

    @Test
    fun `playRepairCard removes blocked tool discards card and advances turn`() {
        gameService.startGame(createPlayers())

        val currentState = gameService.getGameState()
        val currentPlayerId = currentState.currentPlayerId!!
        val targetPlayerId = currentState.players.first { it.playerId != currentPlayerId }.playerId

        val updatedPlayers = currentState.players.map { playerTurn ->
            if (playerTurn.playerId == targetPlayerId) {
                playerTurn.copy(blockedTools = setOf(ToolType.PICKAXE))
            } else {
                playerTurn
            }
        }
        gameService.setPlayersForTest(updatedPlayers)

        val repairCard = TunnelCard(
            id = "repair-1",
            type = CardType.PICKAXE_GREEN,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(repairCard))

        gameService.playRepairCard(
            playerId = currentPlayerId,
            cardId = repairCard.id,
            targetPlayerId = targetPlayerId,
            tool = ToolType.PICKAXE
        )

        val updatedTarget = gameService.getGameState().players.first { it.playerId == targetPlayerId }
        assertFalse(updatedTarget.blockedTools.contains(ToolType.PICKAXE))

        assertTrue(gameService.getDiscardPile().any { it.id == repairCard.id })
        assertFalse(gameService.getHandForPlayer(currentPlayerId).any { it.id == repairCard.id })
        assertNotEquals(currentPlayerId, gameService.getGameState().currentPlayerId)
    }

    @Test
    fun `playMapCard reveals goal card stores known goal discards card and advances turn`() {
        gameService.startGame(createPlayers())

        val currentPlayerId = gameService.getGameState().currentPlayerId!!
        val goalPosition = BoardPosition(row = 2, column = 10)

        val mapCard = TunnelCard(
            id = "map-1",
            type = CardType.MAPCARD,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(mapCard))

        val result = gameService.playMapCard(
            playerId = currentPlayerId,
            cardId = mapCard.id,
            targetPosition = goalPosition
        )

        assertEquals(goalPosition, result.position)
        assertEquals(CardType.GOAL, result.card.type)

        val knownGoals = gameService.getKnownGoalsForPlayer(currentPlayerId)
        assertTrue(knownGoals.containsKey(goalPosition))

        assertTrue(gameService.getDiscardPile().any { it.id == mapCard.id })
        assertFalse(gameService.getHandForPlayer(currentPlayerId).any { it.id == mapCard.id })
        assertNotEquals(currentPlayerId, gameService.getGameState().currentPlayerId)
    }

    @Test
    fun `playRockfallCard removes path card from board discards card and advances turn`() {
        gameService.startGame(createPlayers())

        val currentPlayerId = gameService.getGameState().currentPlayerId!!
        val targetPosition = BoardPosition(row = 4, column = 3)

        val pathCard = TunnelCard(
            id = "path-target",
            type = CardType.PATH,
            connections = setOf(Direction.LEFT, Direction.RIGHT)
        )

        val updatedPlacements = gameService.getGameState().boardPlacements + PlacedTunnelCard(
            position = targetPosition,
            card = pathCard
        )
        gameService.setBoardPlacementsForTest(updatedPlacements)

        val rockfallCard = TunnelCard(
            id = "rockfall-1",
            type = CardType.ROCKFALL,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(rockfallCard))

        gameService.playRockfallCard(
            playerId = currentPlayerId,
            cardId = rockfallCard.id,
            targetPosition = targetPosition
        )

        assertFalse(gameService.getGameState().boardPlacements.any { it.position == targetPosition })
        assertTrue(gameService.getDiscardPile().any { it.id == rockfallCard.id })
        assertFalse(gameService.getHandForPlayer(currentPlayerId).any { it.id == rockfallCard.id })
        assertNotEquals(currentPlayerId, gameService.getGameState().currentPlayerId)
    }
}