package com.aau.saboteur.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Tests for Player hand management using immutable copy operations
class PlayerTest {

    private fun card(id: String) = TunnelCard(id, CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))

    @Test
    fun `player starts with empty hand`() {
        assertTrue(Player("p1", "Alice").hand.isEmpty())
    }

    @Test
    fun `adding card to hand produces updated player`() {
        val player = Player("p1", "Alice")
        val updated = player.copy(hand = player.hand + card("c1"))
        assertEquals(1, updated.hand.size)
        assertEquals("c1", updated.hand[0].id)
    }

    @Test
    fun `adding multiple cards to hand produces updated player`() {
        val player = Player("p1", "Alice")
        val updated = player
            .copy(hand = player.hand + card("c1"))
            .let { it.copy(hand = it.hand + card("c2")) }
            .let { it.copy(hand = it.hand + card("c3")) }
        assertEquals(3, updated.hand.size)
    }

    @Test
    fun `removing card from hand produces updated player`() {
        val c = card("c1")
        val player = Player("p1", "Alice", hand = listOf(c))
        val updated = player.copy(hand = player.hand - c)
        assertTrue(updated.hand.isEmpty())
    }

    @Test
    fun `removing card only removes one instance when duplicates exist`() {
        val c = card("c1")
        val player = Player("p1", "Alice", hand = listOf(c, c))
        val updated = player.copy(hand = player.hand - c)
        assertEquals(1, updated.hand.size)
    }

    @Test
    fun `removing absent card leaves hand unchanged`() {
        val player = Player("p1", "Alice", hand = listOf(card("c1")))
        val updated = player.copy(hand = player.hand - card("c99"))
        assertEquals(1, updated.hand.size)
    }
}