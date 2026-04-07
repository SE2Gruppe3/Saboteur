package com.aau.server

import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.TunnelCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

// Tests für MoveValidator: Validierung von Kartenzügen und Platzierung
class MoveValidatorTest {

    private fun validator() = MoveValidator(GameBoard())

    private fun card(id: String, connections: Set<Direction>) =
        TunnelCard(id, CardType.PATH, connections)

    // ── isValidMove Tests ──────────────────────────────────────────────────────

    @Test
    fun `isValidMove returns true for valid placement to the right of start`() {
        val validator = validator()
        val card = card("c1", setOf(Direction.LEFT, Direction.RIGHT))
        assertTrue(validator.isValidMove(card, 1, 0))
    }

    @Test
    fun `isValidMove returns true for valid placement above start`() {
        val validator = validator()
        val card = card("c2", setOf(Direction.TOP, Direction.BOTTOM))
        assertTrue(validator.isValidMove(card, 0, -1))
    }

    @Test
    fun `isValidMove returns true for valid placement below start`() {
        val validator = validator()
        val card = card("c3", setOf(Direction.TOP, Direction.BOTTOM))
        assertTrue(validator.isValidMove(card, 0, 1))
    }

    @Test
    fun `isValidMove returns true for valid placement to the left of start`() {
        val validator = validator()
        val card = card("c4", setOf(Direction.LEFT, Direction.RIGHT))
        assertTrue(validator.isValidMove(card, -1, 0))
    }

    @Test
    fun `isValidMove returns false for invalid placement with incompatible connections`() {
        val validator = validator()
        val card = card("c5", setOf(Direction.TOP, Direction.BOTTOM))
        assertFalse(validator.isValidMove(card, 1, 0)) // Right of start needs LEFT
    }

    @Test
    fun `isValidMove returns false when placing on occupied cell`() {
        val validator = validator()
        val card = card("c6", setOf(Direction.TOP, Direction.BOTTOM, Direction.LEFT, Direction.RIGHT))
        assertFalse(validator.isValidMove(card, 0, 0)) // Start card is here
    }

    @Test
    fun `isValidMove returns false when placing isolated card (no neighbors)`() {
        val validator = validator()
        val card = card("c7", setOf(Direction.LEFT, Direction.RIGHT))
        assertFalse(validator.isValidMove(card, 5, 5)) // Far away, no neighbors
    }

    // ── placeCardIfValid Tests ─────────────────────────────────────────────────

    @Test
    fun `placeCardIfValid places card and returns it when valid`() {
        val validator = validator()
        val card = card("c8", setOf(Direction.LEFT, Direction.RIGHT))
        val result = validator.placeCardIfValid(card, 1, 0)
        assertEquals("c8", result.id)
        assertEquals(card, validator.getGameBoard().getCard(1, 0))
    }

    @Test
    fun `placeCardIfValid places card above start successfully`() {
        val validator = validator()
        val card = card("c9", setOf(Direction.TOP, Direction.BOTTOM))
        validator.placeCardIfValid(card, 0, -1)
        assertEquals(card, validator.getGameBoard().getCard(0, -1))
    }

    @Test
    fun `placeCardIfValid throws exception for invalid move`() {
        val validator = validator()
        val card = card("c10", setOf(Direction.TOP, Direction.BOTTOM))
        assertFailsWith<IllegalArgumentException> {
            validator.placeCardIfValid(card, 1, 0) // Incompatible with start
        }
    }

    @Test
    fun `placeCardIfValid exception message contains position info`() {
        val validator = validator()
        val card = card("c11", setOf(Direction.TOP))
        val exception = assertFailsWith<IllegalArgumentException> {
            validator.placeCardIfValid(card, 1, 0)
        }
        assertTrue(exception.message!!.contains("(1, 0)"))
    }

    @Test
    fun `placeCardIfValid throws exception for occupied cell`() {
        val validator = validator()
        val card = card("c12", setOf(Direction.TOP, Direction.BOTTOM, Direction.LEFT, Direction.RIGHT))
        assertFailsWith<IllegalArgumentException> {
            validator.placeCardIfValid(card, 0, 0)
        }
    }

    // ── Chained Placement Tests ────────────────────────────────────────────────

    @Test
    fun `can place multiple cards in a chain`() {
        val validator = validator()

        val card1 = card("c13", setOf(Direction.LEFT, Direction.RIGHT))
        validator.placeCardIfValid(card1, 1, 0)

        val card2 = card("c14", setOf(Direction.LEFT, Direction.RIGHT))
        validator.placeCardIfValid(card2, 2, 0)

        assertEquals(card1, validator.getGameBoard().getCard(1, 0))
        assertEquals(card2, validator.getGameBoard().getCard(2, 0))
    }

    @Test
    fun `cannot place card that breaks connection chain`() {
        val validator = validator()

        // Place first card to the right
        val card1 = card("c15", setOf(Direction.LEFT, Direction.RIGHT))
        validator.placeCardIfValid(card1, 1, 0)

        // Try to place incompatible card next to it
        val card2 = card("c16", setOf(Direction.TOP, Direction.BOTTOM))
        assertFailsWith<IllegalArgumentException> {
            validator.placeCardIfValid(card2, 2, 0) // No LEFT connection
        }
    }

    @Test
    fun `getGameBoard returns the internal game board`() {
        val validator = validator()
        val board = validator.getGameBoard()
        assertNotNull(board.getCard(0, 0)) // Start card should exist
    }
}