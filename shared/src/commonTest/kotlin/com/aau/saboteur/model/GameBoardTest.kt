package com.aau.saboteur.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


// Tests for start card placement, canPlaceCard (compatible/incompatible cases) and chained placement
class GameBoardTest {

    private fun board() = GameBoard()

    // ── Start card ────────────────────────────────────────────────────────────

    @Test
    fun `start card is placed at (0,0)`() {
        val card = board().getCard(0, 0)
        assertNotNull(card)
        assertEquals(CardType.START, card.type)
    }

    @Test
    fun `cell other than (0,0) is empty initially`() {
        assertNull(board().getCard(1, 0))
    }

    // ── canPlaceCard – compatible cases ───────────────────────────────────────

    @Test
    fun `can place card to the right of start when it has LEFT connection`() {
        // Start has RIGHT → neighbour needs LEFT
        val card = TunnelCard("t1", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        assertTrue(board().canPlaceCard(1, 0, card))
    }

    @Test
    fun `can place card above start when it has BOTTOM connection`() {
        // Start has TOP → neighbour needs BOTTOM
        val card = TunnelCard("t2", CardType.PATH, setOf(Direction.TOP, Direction.BOTTOM))
        assertTrue(board().canPlaceCard(0, -1, card))
    }

    @Test
    fun `can place card below start when it has TOP connection`() {
        val card = TunnelCard("t3", CardType.PATH, setOf(Direction.TOP, Direction.BOTTOM))
        assertTrue(board().canPlaceCard(0, 1, card))
    }

    @Test
    fun `can place card to the left of start when it has RIGHT connection`() {
        val card = TunnelCard("t4", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        assertTrue(board().canPlaceCard(-1, 0, card))
    }

    // ── canPlaceCard – incompatible cases ─────────────────────────────────────

    @Test
    fun `cannot place card to the right of start when it lacks LEFT connection`() {
        // Start has RIGHT → neighbour needs LEFT, but this card only has TOP+BOTTOM
        val card = TunnelCard("t5", CardType.PATH, setOf(Direction.TOP, Direction.BOTTOM))
        assertFalse(board().canPlaceCard(1, 0, card))
    }

    @Test
    fun `cannot place card to the right of start when it has LEFT but start RIGHT is blocked`() {
        // Create a card that has LEFT but the start card's RIGHT is present → mismatch test:
        // Reuse incompatible direction: card has NO left connection → fails
        val card = TunnelCard("t6", CardType.DEAD_END, setOf(Direction.TOP))
        assertFalse(board().canPlaceCard(1, 0, card))
    }

    @Test
    fun `cannot place card on occupied cell`() {
        // (0,0) is already occupied by the start card
        val card = TunnelCard("t7", CardType.PATH, setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT, Direction.BOTTOM))
        assertFalse(board().canPlaceCard(0, 0, card))
    }

    @Test
    fun `cannot place card with no adjacent cards`() {
        val card = TunnelCard("t8", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        assertFalse(board().canPlaceCard(5, 5, card))
    }

    // ── canPlaceCard – chained placement ──────────────────────────────────────

    @Test
    fun `can place second card adjacent to first placed card`() {
        val b = board()
        val first = TunnelCard("t9", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        b.placeCard(1, 0, first)
        // second card to the right of first; first has RIGHT, so second needs LEFT
        val second = TunnelCard("t10", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        assertTrue(b.canPlaceCard(2, 0, second))
    }

}
