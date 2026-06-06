package com.aau.server

import com.aau.saboteur.model.*
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
    }

    @Test
    fun `exercise GameStartResult data class`() {
        val gameState = GameState(emptyList(), null)
        val startCard = TunnelCard("s", CardType.START, emptySet())
        val dist = CardDistributionResult(emptyMap(), emptyList(), emptyList(), startCard)
        val result1 = GameStartResult(gameState, emptyMap(), dist)
        val result2 = GameStartResult(gameState, emptyMap(), dist)

        assertEquals(result1, result2)
        assertNotNull(result1.toString())
    }

    @Test
    fun `exercise UserEntity properties`() {
        val u1 = UserEntity(1L, "u", "p")
        assertEquals(1L, u1.id)
        assertEquals("u", u1.username)
        assertEquals("p", u1.passwordHash)
    }

    @Test
    fun `exercise ToolType enum`() {
        assertEquals(ToolType.LANTERN, ToolType.valueOf("LANTERN"))
        assertEquals(ToolType.PICKAXE, ToolType.valueOf("PICKAXE"))
        assertEquals(ToolType.CART, ToolType.valueOf("CART"))
    }

    @Test
    fun `exercise MapResult data class`() {
        val card = TunnelCard("goal1", CardType.GOAL, emptySet(), isGoal = true)
        val result1 = MapResult(BoardPosition(2, 10), card)
        assertEquals(BoardPosition(2, 10), result1.position)
        assertEquals(card, result1.card)
    }

    @Test
    fun `exercise LobbyEntity properties`() {
        val now = System.currentTimeMillis()
        val l1 = LobbyEntity("1234", "host1", true, "[]", now)
        assertEquals("1234", l1.lobbyCode)
        assertEquals(true, l1.gameStarted)
    }

    @Test
    fun `exercise GameEntity properties`() {
        val g1 = GameEntity("1234", "p1", "board", "draw", "discard", "hands", "turns", "roles", true, 5)
        assertEquals("1234", g1.lobbyCode)
        assertEquals(true, g1.deckWasEmptied)
        assertEquals(5, g1.passedSinceEmpty)
    }

    @Test
    fun `exercise LobbyEntity default constructor`() {
        val entity = LobbyEntity()

        assertEquals("", entity.lobbyCode)
        assertEquals("", entity.hostId)
        assertEquals(false, entity.gameStarted)
        assertEquals("", entity.playersJson)
        assertNotNull(entity.lastActivity)
    }

    @Test
    fun `exercise UserEntity default constructor and generated playerId`() {
        val entity = UserEntity()

        assertEquals(null, entity.id)
        assertEquals("", entity.username)
        assertEquals("", entity.passwordHash)
        assertNotNull(entity.playerId)
        assertNotEquals("", entity.playerId)
    }

    @Test
    fun `exercise UserEntity full constructor including playerId`() {
        val entity = UserEntity(
            id = 42L,
            username = "basti",
            passwordHash = "hash",
            playerId = "player-123"
        )

        assertEquals(42L, entity.id)
        assertEquals("basti", entity.username)
        assertEquals("hash", entity.passwordHash)
        assertEquals("player-123", entity.playerId)
    }

    @Test
    fun `exercise TurnResult with explicit winner`() {
        val state = GameState()
        val hands = emptyMap<String, List<TunnelCard>>()

        val result = TurnResult(
            updatedGameState = state,
            updatedHands = hands,
            winner = "DWARVES"
        )

        assertEquals(state, result.updatedGameState)
        assertEquals(hands, result.updatedHands)
        assertEquals("DWARVES", result.winner)
        assertNotNull(result.toString())
    }

    @Test
    fun `exercise TurnResult default winner null`() {
        val result = TurnResult(
            updatedGameState = GameState(),
            updatedHands = emptyMap()
        )

        assertEquals(null, result.winner)
    }
}
