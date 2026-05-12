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
    fun `playBlockCard on already blocked tool throws exception`() {
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

        val blockCard = TunnelCard(
            id = "block-duplicate",
            type = CardType.PICKAXE_RED,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(blockCard))

        assertThrows(IllegalArgumentException::class.java) {
            gameService.playBlockCard(
                playerId = currentPlayerId,
                cardId = blockCard.id,
                targetPlayerId = targetPlayerId
            )
        }
    }

    @Test
    fun `playBlockCard allows adding different blocked tool`() {
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

        val blockCard = TunnelCard(
            id = "block-cart",
            type = CardType.CART_RED,
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
        assertTrue(updatedTarget.blockedTools.contains(ToolType.CART))

        assertTrue(gameService.getDiscardPile().any { it.id == blockCard.id })
        assertFalse(gameService.getHandForPlayer(currentPlayerId).any { it.id == blockCard.id })
        assertNotEquals(currentPlayerId, gameService.getGameState().currentPlayerId)
    }

    @Test
    fun `playBlockCard with non block card throws exception`() {
        gameService.startGame(createPlayers())

        val currentPlayerId = gameService.getGameState().currentPlayerId!!
        val targetPlayerId = gameService.getGameState()
            .players
            .first { it.playerId != currentPlayerId }
            .playerId

        val nonBlockCard = TunnelCard(
            id = "not-block",
            type = CardType.MAPCARD,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(nonBlockCard))

        assertThrows(IllegalArgumentException::class.java) {
            gameService.playBlockCard(
                playerId = currentPlayerId,
                cardId = nonBlockCard.id,
                targetPlayerId = targetPlayerId
            )
        }
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
    fun `playRepairCard with double repair card removes only selected tool`() {
        gameService.startGame(createPlayers())

        val currentState = gameService.getGameState()
        val currentPlayerId = currentState.currentPlayerId!!
        val targetPlayerId = currentState.players.first { it.playerId != currentPlayerId }.playerId

        val updatedPlayers = currentState.players.map { playerTurn ->
            if (playerTurn.playerId == targetPlayerId) {
                playerTurn.copy(blockedTools = setOf(ToolType.PICKAXE, ToolType.CART))
            } else {
                playerTurn
            }
        }
        gameService.setPlayersForTest(updatedPlayers)

        val repairCard = TunnelCard(
            id = "double-repair-1",
            type = CardType.DOUBLE_PICKAXE_CART,
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
        assertTrue(updatedTarget.blockedTools.contains(ToolType.CART))

        assertTrue(gameService.getDiscardPile().any { it.id == repairCard.id })
        assertFalse(gameService.getHandForPlayer(currentPlayerId).any { it.id == repairCard.id })
        assertNotEquals(currentPlayerId, gameService.getGameState().currentPlayerId)
    }

    @Test
    fun `playRepairCard on tool that is not blocked throws exception`() {
        gameService.startGame(createPlayers())

        val currentState = gameService.getGameState()
        val currentPlayerId = currentState.currentPlayerId!!
        val targetPlayerId = currentState.players.first { it.playerId != currentPlayerId }.playerId

        val updatedPlayers = currentState.players.map { playerTurn ->
            if (playerTurn.playerId == targetPlayerId) {
                playerTurn.copy(blockedTools = setOf(ToolType.CART))
            } else {
                playerTurn
            }
        }
        gameService.setPlayersForTest(updatedPlayers)

        val repairCard = TunnelCard(
            id = "repair-invalid",
            type = CardType.PICKAXE_GREEN,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(repairCard))

        assertThrows(IllegalArgumentException::class.java) {
            gameService.playRepairCard(
                playerId = currentPlayerId,
                cardId = repairCard.id,
                targetPlayerId = targetPlayerId,
                tool = ToolType.PICKAXE
            )
        }
    }

    @Test
    fun `playRepairCard with wrong repair card for selected tool throws exception`() {
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
            id = "repair-wrong-card",
            type = CardType.CART_GREEN,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(repairCard))

        assertThrows(IllegalArgumentException::class.java) {
            gameService.playRepairCard(
                playerId = currentPlayerId,
                cardId = repairCard.id,
                targetPlayerId = targetPlayerId,
                tool = ToolType.PICKAXE
            )
        }
    }

    @Test
    fun `playRepairCard with non repair card throws exception`() {
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

        val nonRepairCard = TunnelCard(
            id = "not-repair",
            type = CardType.ROCKFALL,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(nonRepairCard))

        assertThrows(IllegalArgumentException::class.java) {
            gameService.playRepairCard(
                playerId = currentPlayerId,
                cardId = nonRepairCard.id,
                targetPlayerId = targetPlayerId,
                tool = ToolType.PICKAXE
            )
        }
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
    fun `playMapCard on normal path card throws exception`() {
        gameService.startGame(createPlayers())

        val currentPlayerId = gameService.getGameState().currentPlayerId!!
        val targetPosition = BoardPosition(row = 4, column = 3)

        val pathCard = TunnelCard(
            id = "path-1",
            type = CardType.PATH,
            connections = setOf(Direction.LEFT, Direction.RIGHT)
        )

        val updatedPlacements = gameService.getGameState().boardPlacements + PlacedTunnelCard(
            position = targetPosition,
            card = pathCard
        )
        gameService.setBoardPlacementsForTest(updatedPlacements)

        val mapCard = TunnelCard(
            id = "map-2",
            type = CardType.MAPCARD,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(mapCard))

        assertThrows(IllegalArgumentException::class.java) {
            gameService.playMapCard(
                playerId = currentPlayerId,
                cardId = mapCard.id,
                targetPosition = targetPosition
            )
        }
    }

    @Test
    fun `playMapCard on empty position throws exception`() {
        gameService.startGame(createPlayers())

        val currentPlayerId = gameService.getGameState().currentPlayerId!!
        val emptyPosition = BoardPosition(row = 0, column = 0)

        val mapCard = TunnelCard(
            id = "map-empty",
            type = CardType.MAPCARD,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(mapCard))

        assertThrows(IllegalArgumentException::class.java) {
            gameService.playMapCard(
                playerId = currentPlayerId,
                cardId = mapCard.id,
                targetPosition = emptyPosition
            )
        }
    }

    @Test
    fun `playMapCard on start card throws exception`() {
        gameService.startGame(createPlayers())

        val currentPlayerId = gameService.getGameState().currentPlayerId!!
        val startPosition = BoardPosition(row = 4, column = 2)

        val mapCard = TunnelCard(
            id = "map-start",
            type = CardType.MAPCARD,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(mapCard))

        assertThrows(IllegalArgumentException::class.java) {
            gameService.playMapCard(
                playerId = currentPlayerId,
                cardId = mapCard.id,
                targetPosition = startPosition
            )
        }
    }

    @Test
    fun `playMapCard with non map card throws exception`() {
        gameService.startGame(createPlayers())

        val currentPlayerId = gameService.getGameState().currentPlayerId!!
        val goalPosition = BoardPosition(row = 2, column = 10)

        val nonMapCard = TunnelCard(
            id = "not-map",
            type = CardType.PICKAXE_RED,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(nonMapCard))

        assertThrows(IllegalArgumentException::class.java) {
            gameService.playMapCard(
                playerId = currentPlayerId,
                cardId = nonMapCard.id,
                targetPosition = goalPosition
            )
        }
    }

    @Test
    fun `playRockfallCard on start card throws exception`() {
        gameService.startGame(createPlayers())

        val currentPlayerId = gameService.getGameState().currentPlayerId!!
        val startPosition = BoardPosition(row = 4, column = 2)

        val rockfallCard = TunnelCard(
            id = "rockfall-start",
            type = CardType.ROCKFALL,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(rockfallCard))

        assertThrows(IllegalArgumentException::class.java) {
            gameService.playRockfallCard(
                playerId = currentPlayerId,
                cardId = rockfallCard.id,
                targetPosition = startPosition
            )
        }
    }

    @Test
    fun `playRockfallCard on goal card throws exception`() {
        gameService.startGame(createPlayers())

        val currentPlayerId = gameService.getGameState().currentPlayerId!!
        val goalPosition = BoardPosition(row = 2, column = 10)

        val rockfallCard = TunnelCard(
            id = "rockfall-goal",
            type = CardType.ROCKFALL,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(rockfallCard))

        assertThrows(IllegalArgumentException::class.java) {
            gameService.playRockfallCard(
                playerId = currentPlayerId,
                cardId = rockfallCard.id,
                targetPosition = goalPosition
            )
        }
    }

    @Test
    fun `playRockfallCard on empty position throws exception`() {
        gameService.startGame(createPlayers())

        val currentPlayerId = gameService.getGameState().currentPlayerId!!
        val emptyPosition = BoardPosition(row = 0, column = 0)

        val rockfallCard = TunnelCard(
            id = "rockfall-empty",
            type = CardType.ROCKFALL,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(rockfallCard))

        assertThrows(IllegalArgumentException::class.java) {
            gameService.playRockfallCard(
                playerId = currentPlayerId,
                cardId = rockfallCard.id,
                targetPosition = emptyPosition
            )
        }
    }

    @Test
    fun `playRockfallCard with non rockfall card throws exception`() {
        gameService.startGame(createPlayers())

        val currentPlayerId = gameService.getGameState().currentPlayerId!!
        val targetPosition = BoardPosition(row = 4, column = 3)

        val pathCard = TunnelCard(
            id = "path-for-non-rockfall",
            type = CardType.PATH,
            connections = setOf(Direction.LEFT, Direction.RIGHT)
        )

        val updatedPlacements = gameService.getGameState().boardPlacements + PlacedTunnelCard(
            position = targetPosition,
            card = pathCard
        )
        gameService.setBoardPlacementsForTest(updatedPlacements)

        val nonRockfallCard = TunnelCard(
            id = "not-rockfall",
            type = CardType.MAPCARD,
            connections = emptySet()
        )

        gameService.setHandForPlayer(currentPlayerId, listOf(nonRockfallCard))

        assertThrows(IllegalArgumentException::class.java) {
            gameService.playRockfallCard(
                playerId = currentPlayerId,
                cardId = nonRockfallCard.id,
                targetPosition = targetPosition
            )
        }
    }

    @Test
    fun `playRockfallCard removes path card from board discards both cards and advances turn`() {
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
        assertTrue(gameService.getDiscardPile().any { it.id == pathCard.id })
        assertFalse(gameService.getHandForPlayer(currentPlayerId).any { it.id == rockfallCard.id })
        assertNotEquals(currentPlayerId, gameService.getGameState().currentPlayerId)
    }
}