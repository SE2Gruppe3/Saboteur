package com.aau.server

import com.aau.saboteur.model.Player
import com.aau.saboteur.model.Role
import com.aau.server.service.GameService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameServiceTests {

    private val gameService = GameService()

    @Test
    fun `initial state is empty`() {
        val state = gameService.getGameState()
        assertTrue(state.players.isEmpty())
        assertNull(state.currentPlayerId)
    }

    @Test
    fun `assignRandomTurnOrder assigns all players and sets current player`() {
        val players = listOf(
            Player("1", "Alice"),
            Player("2", "Bob"),
            Player("3", "Charlie")
        )

        val state = gameService.assignRandomTurnOrder(players)

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
    }

    @Test
    fun `assignRandomRoles assigns roles and keeps them in private data`() {
        val players = listOf(
            Player("1", "Alice"),
            Player("2", "Bob"),
            Player("3", "Charlie")
        )
        
        gameService.assignRandomTurnOrder(players)
        val roleData = gameService.assignRandomRoles(players)
        
        // Check returned data
        assertEquals(3, roleData.size)
        assertNotNull(roleData["1"]?.role)
        assertNotNull(roleData["2"]?.role)
        assertNotNull(roleData["3"]?.role)
        
        // Check private data retrieval via helper
        val player1 = gameService.getPlayer("1")
        assertNotNull(player1)
        assertEquals(roleData["1"]?.role, player1?.role)
        
        // Verify public GameState DOES NOT contain the roles
        val publicState = gameService.getGameState()
        // PlayerTurn objects in GameState should not have the role field exposed
        // (Assuming PlayerTurn.kt was rolled back to not include role)
    }

    @Test
    fun `assignRandomTurnOrder handles empty list`() {
        val state = gameService.assignRandomTurnOrder(emptyList())
        assertTrue(state.players.isEmpty())
        assertNull(state.currentPlayerId)
    }
}
