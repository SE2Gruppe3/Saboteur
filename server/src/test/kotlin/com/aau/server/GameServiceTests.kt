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

    private lateinit var gameService: GameService

    @BeforeEach
    fun setup() {
        gameService = GameService()
    }

    @Test
    fun `getGameState returns initial empty state`() {
        val state = gameService.getGameState()

        assertEquals(GameState(players = emptyList(), currentPlayerId = null), state)
    }

    @Test
    fun `getPlayer returns null for unknown player`() {
        assertNull(gameService.getPlayer("unknown"))
    }

    @Test
    fun `startGame with valid player count returns initialized result`() {
        val players = listOf(
            Player("1", "Alice"),
            Player("2", "Bob"),
            Player("3", "Charlie")
        )

        val result = gameService.startGame(players)

        assertEquals(3, result.gameState.players.size)
        assertNotNull(result.gameState.currentPlayerId)
        assertEquals(4, result.gameState.boardPlacements.size)
        assertEquals(3, result.playerRoles.size)
        assertEquals(3, result.cardDistribution.hands.size)
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

        players.forEach { player ->
            val storedPlayer = gameService.getPlayer(player.id)
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