package com.aau.saboteur.network.game

import com.aau.shared.game.CreateGameRequest
import com.aau.shared.game.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameJsonTest {

    @Test
    fun `CreateGameRequest toJson produces correct json`() {
        val request = CreateGameRequest(
            players = listOf(
                Player("1", "Alice"),
                Player("2", "Bob")
            )
        )
        val json = request.toJson()
        
        assertTrue(json.contains("\"id\":\"1\""))
        assertTrue(json.contains("\"name\":\"Alice\""))
        assertTrue(json.contains("\"id\":\"2\""))
        assertTrue(json.contains("\"name\":\"Bob\""))
    }

    @Test
    fun `String toGameState parses valid json correctly`() {
        val json = """
            {
                "players": [
                    {"playerId": "p1", "playerName": "Alice", "turnOrder": 1},
                    {"player_id": "p2", "player_name": "Bob", "turn_order": 2}
                ],
                "currentPlayerId": "p1"
            }
        """.trimIndent()
        
        val gameState = json.toGameState()
        
        assertEquals("p1", gameState.currentPlayerId)
        assertEquals(2, gameState.players.size)
        
        assertEquals("p1", gameState.players[0].playerId)
        assertEquals("Alice", gameState.players[0].playerName)
        assertEquals(1, gameState.players[0].turnOrder)
        
        assertEquals("p2", gameState.players[1].playerId)
        assertEquals("Bob", gameState.players[1].playerName)
        assertEquals(2, gameState.players[1].turnOrder)
    }

    @Test
    fun `String toGameState handles empty json`() {
        val json = "{}"
        val gameState = json.toGameState()
        
        assertTrue(gameState.players.isEmpty())
        assertNull(gameState.currentPlayerId)
    }
}
