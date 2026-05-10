package com.aau.server

import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.model.CardType
import com.aau.server.model.CardDistributionResult
import com.aau.server.model.GameStartResult
import com.aau.server.model.UserEntity
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
}
