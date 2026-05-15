package com.aau.server

import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.model.CardType
import com.aau.server.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class ModelCoverageTest {

    @Test
    fun `exercise CardDistributionResult data class`() {
        val startCard = TunnelCard("s", CardType.START, emptySet())
        val result1 = CardDistributionResult(emptyMap(), emptyList(), emptyList(), startCard)
        val result2 = CardDistributionResult(emptyMap(), emptyList(), emptyList(), startCard)
        
        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
        assertNotEquals(result1, Any())
        assertNotNull(result1.toString())
        
        assertEquals(emptyMap(), result1.hands)
        assertEquals(emptyList(), result1.drawPile)
        assertEquals(emptyList(), result1.goalCards)
        assertEquals(startCard, result1.startCard)
    }

    @Test
    fun `exercise GameStartResult data class`() {
        val gameState = GameState(emptyList(), null)
        val startCard = TunnelCard("s", CardType.START, emptySet())
        val dist = CardDistributionResult(emptyMap(), emptyList(), emptyList(), startCard)
        val result1 = GameStartResult(gameState, emptyMap(), dist)
        val result2 = GameStartResult(gameState, emptyMap(), dist)

        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
        assertNotNull(result1.toString())
        
        assertEquals(gameState, result1.gameState)
        assertEquals(emptyMap(), result1.playerRoles)
        assertEquals(dist, result1.cardDistribution)
    }

    @Test
    fun `exercise UserEntity properties`() {
        val u1 = UserEntity(1L, "u", "p")
        assertEquals(1L, u1.id)
        assertEquals("u", u1.username)
        assertEquals("p", u1.passwordHash)
        
        val u2 = UserEntity()
        u2.id = 5L
        u2.username = "test"
        u2.passwordHash = "hash"
        assertEquals(5L, u2.id)
        assertEquals("test", u2.username)
        assertEquals("hash", u2.passwordHash)
    }

    @Test
    fun `exercise LobbyEntity properties`() {
        val now = System.currentTimeMillis()
        val l1 = LobbyEntity("1234", "host1", true, "[]", now)
        assertEquals("1234", l1.lobbyCode)
        assertEquals("host1", l1.hostId)
        assertEquals(true, l1.gameStarted)
        assertEquals("[]", l1.playersJson)
        assertEquals(now, l1.lastActivity)

        val l2 = LobbyEntity()
        l2.lobbyCode = "5678"
        l2.hostId = "host2"
        l2.gameStarted = false
        l2.playersJson = "[player]"
        l2.lastActivity = 1000L
        assertEquals("5678", l2.lobbyCode)
        assertEquals("host2", l2.hostId)
        assertEquals(false, l2.gameStarted)
        assertEquals("[player]", l2.playersJson)
        assertEquals(1000L, l2.lastActivity)
    }

    @Test
    fun `exercise GameEntity properties`() {
        val g1 = GameEntity("1234", "p1", "board", "draw", "discard", "hands", "turns", "roles", true, 5)
        assertEquals("1234", g1.lobbyCode)
        assertEquals("p1", g1.currentPlayerId)
        assertEquals("board", g1.boardJson)
        assertEquals("draw", g1.drawPileJson)
        assertEquals("discard", g1.discardPileJson)
        assertEquals("hands", g1.handsJson)
        assertEquals("turns", g1.playersTurnJson)
        assertEquals("roles", g1.playerRolesJson)
        assertEquals(true, g1.deckWasEmptied)
        assertEquals(5, g1.passedSinceEmpty)

        val g2 = GameEntity()
        g2.lobbyCode = "5"
        assertEquals("5", g2.lobbyCode)
    }
}
