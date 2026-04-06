package com.aau.server

import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.TunnelCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Tests for TunnelCard.rotated180() direction-flipping logic
class TunnelCardTest {

    @Test
    fun `rotated180 flips TOP to BOTTOM`() {
        val card = TunnelCard("c1", CardType.PATH, setOf(Direction.TOP))
        val rotated = card.rotated180()
        assertTrue(Direction.BOTTOM in rotated.connections)
        assertTrue(Direction.TOP !in rotated.connections)
    }

    @Test
    fun `rotated180 flips BOTTOM to TOP`() {
        val card = TunnelCard("c2", CardType.PATH, setOf(Direction.BOTTOM))
        val rotated = card.rotated180()
        assertTrue(Direction.TOP in rotated.connections)
        assertTrue(Direction.BOTTOM !in rotated.connections)
    }

    @Test
    fun `rotated180 flips LEFT to RIGHT`() {
        val card = TunnelCard("c3", CardType.PATH, setOf(Direction.LEFT))
        val rotated = card.rotated180()
        assertTrue(Direction.RIGHT in rotated.connections)
        assertTrue(Direction.LEFT !in rotated.connections)
    }

    @Test
    fun `rotated180 flips RIGHT to LEFT`() {
        val card = TunnelCard("c4", CardType.PATH, setOf(Direction.RIGHT))
        val rotated = card.rotated180()
        assertTrue(Direction.LEFT in rotated.connections)
        assertTrue(Direction.RIGHT !in rotated.connections)
    }

    @Test
    fun `rotated180 flips all directions correctly`() {
        val card = TunnelCard("c5", CardType.PATH, setOf(Direction.TOP, Direction.LEFT, Direction.BOTTOM))
        val rotated = card.rotated180()
        assertEquals(setOf(Direction.BOTTOM, Direction.RIGHT, Direction.TOP), rotated.connections)
    }

    @Test
    fun `rotated180 sets isRotated to true`() {
        val card = TunnelCard("c6", CardType.PATH, setOf(Direction.TOP))
        assertEquals(true, card.rotated180().isRotated)
    }

    @Test
    fun `rotated180 preserves id, type and isRevealed`() {
        val card = TunnelCard("my_card", CardType.DEAD_END, setOf(Direction.LEFT), isRevealed = true)
        val rotated = card.rotated180()
        assertEquals("my_card", rotated.id)
        assertEquals(CardType.DEAD_END, rotated.type)
        assertEquals(true, rotated.isRevealed)
    }
}
