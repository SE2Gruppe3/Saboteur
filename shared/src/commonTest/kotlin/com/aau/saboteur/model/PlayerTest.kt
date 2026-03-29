package com.aau.saboteur.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Tests for addCard() and removeCard() including edge cases (duplicates, absent card)
class PlayerTest {

    private fun card(id: String) = TunnelCard(id, CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))

    @Test
    fun `player starts with empty hand`() {
        assertTrue(Player("p1", "Alice").hand.isEmpty())
    }

    @Test
    fun `addCard adds card to hand`() {
        val player = Player("p1", "Alice")
        player.addCard(card("c1"))
        assertEquals(1, player.hand.size)
        assertEquals("c1", player.hand[0].id)
    }

    @Test
    fun `addCard can add multiple cards`() {
        val player = Player("p1", "Alice")
        player.addCard(card("c1"))
        player.addCard(card("c2"))
        player.addCard(card("c3"))
        assertEquals(3, player.hand.size)
    }

    @Test
    fun `removeCard removes card from hand`() {
        val player = Player("p1", "Alice")
        val c = card("c1")
        player.addCard(c)
        player.removeCard(c)
        assertTrue(player.hand.isEmpty())
    }

    @Test
    fun `removeCard only removes one instance when duplicates exist`() {
        val player = Player("p1", "Alice")
        val c = card("c1")
        player.addCard(c)
        player.addCard(c)
        player.removeCard(c)
        assertEquals(1, player.hand.size)
    }

    @Test
    fun `removeCard on absent card leaves hand unchanged`() {
        val player = Player("p1", "Alice")
        player.addCard(card("c1"))
        player.removeCard(card("c99"))
        assertEquals(1, player.hand.size)
    }
}
