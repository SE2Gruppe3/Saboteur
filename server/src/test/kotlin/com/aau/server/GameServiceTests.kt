package com.aau.server

import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player
import com.aau.server.service.GameService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GameServiceTests {

    private val gameService = GameService()
    private val lobbyCode = "TEST_LOBBY"

    @Test
    fun `getPlayer returns null for unknown player`() {
        assertNull(gameService.getPlayer(lobbyCode, "unknown"))
    }

    @Test
    fun `startGame with valid player count returns initialized result`() {
        val players = listOf(
            Player("1", "Alice"),
            Player("2", "Bob"),
            Player("3", "Charlie")
        )

        val result = gameService.startGame(players)
        gameService.setPlayerData(lobbyCode, result.playerRoles)

        // Verify turn order
        val state = result.gameState
        assertEquals(3, state.players.size)
        val turnOrders = state.players.map { it.turnOrder }.sorted()
        assertEquals(listOf(1, 2, 3), turnOrders)
        
        val playerIds = state.players.map { it.playerId }.toSet()
        assertEquals(setOf("1", "2", "3"), playerIds)
        
        assertNotNull(state.currentPlayerId)
        assertTrue(playerIds.contains(state.currentPlayerId))
        
        // Verify current player is indeed the first in turn order
        val firstPlayer = state.players.minBy { it.turnOrder }
        assertEquals(firstPlayer.playerId, state.currentPlayerId)

        // Verify roles
        val roleData = result.playerRoles
        assertEquals(3, roleData.size)
        assertNotNull(roleData["1"]?.role)
        assertNotNull(roleData["2"]?.role)
        assertNotNull(roleData["3"]?.role)
        
        // Verify data retrieval
        val player1 = gameService.getPlayer(lobbyCode, "1")
        assertNotNull(player1)
        assertEquals(roleData["1"]?.role, player1?.role)
        
        // Non-existent player
        assertNull(gameService.getPlayer(lobbyCode, "999"))

        // Verify card distribution
        val cardDist = result.cardDistribution
        assertEquals(3, cardDist.hands.size)
        // Saboteur rules: 3 players get 6 cards each
        cardDist.hands.values.forEach { hand ->
            assertEquals(6, hand.size)
        }
        
        // Verify board placements (1 start, 3 goals)
        assertEquals(4, state.boardPlacements.size)
    }

    @Test
    fun `startGame assigns turn orders from one to player count`() {
        val players = listOf(
            Player("1", "Alice"),
            Player("2", "Bob"),
            Player("3", "Charlie"),
            Player("4", "Diana")
        )

        val result = gameService.startGame(players)
        val turnOrders = result.gameState.players.map { it.turnOrder }.sorted()

        assertEquals(listOf(1, 2, 3, 4), turnOrders)
    }

    @Test
    fun `startGame creates one start card and three goal cards`() {
        val players = listOf(
            Player("1", "Alice"),
            Player("2", "Bob"),
            Player("3", "Charlie")
        )

        val result = gameService.startGame(players)
        val placements = result.gameState.boardPlacements

        assertEquals(1, placements.count { it.card.type == CardType.START })
        assertEquals(3, placements.count { it.card.type == CardType.GOAL })
    }

    @Test
    fun `startGame stores assigned players in player data`() {
        val players = listOf(
            Player("1", "Alice"),
            Player("2", "Bob"),
            Player("3", "Charlie")
        )

        val result = gameService.startGame(players)
        gameService.setPlayerData(lobbyCode, result.playerRoles)

        players.forEach { player ->
            val storedPlayer = gameService.getPlayer(lobbyCode, player.id)
            assertNotNull(storedPlayer)
            assertEquals(player.id, storedPlayer!!.id)
            assertEquals(player.name, storedPlayer.name)
        }

        assertEquals(result.playerRoles.keys.toSet(), players.map { it.id }.toSet())
    }

    @Test
    fun `startGame with too few players throws exception`() {
        val players = listOf(
            Player("1", "Alice"),
            Player("2", "Bob")
        )

        val exception = assertThrows<IllegalArgumentException> {
            gameService.startGame(players)
        }

        assertEquals("Game requires between 3 and 10 players", exception.message)
    }

    @Test
    fun `startGame with too many players throws exception`() {
        val players = (1..11).map { index ->
            Player(index.toString(), "Player$index")
        }

        val exception = assertThrows<IllegalArgumentException> {
            gameService.startGame(players)
        }

        assertEquals("Game requires between 3 and 10 players", exception.message)
    }
}
