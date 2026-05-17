package com.aau.server

import com.aau.saboteur.model.Player
import com.aau.server.service.GameService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameServiceTests {

    private val gameService = GameService()
    private val lobbyCode = "TEST_LOBBY"

    @Test
    fun `startGame initializes everything correctly`() {
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
    }

    @Test
    fun `startGame handles invalid player count`() {
        // Too few
        assertThrows(IllegalArgumentException::class.java) {
            gameService.startGame(listOf(Player("1", "A"), Player("2", "B")))
        }
        // Too many
        val tooMany = (1..11).map { Player(it.toString(), "P$it") }
        assertThrows(IllegalArgumentException::class.java) {
            gameService.startGame(tooMany)
        }
    }
}
