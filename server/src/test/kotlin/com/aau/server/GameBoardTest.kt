package com.aau.server

import com.aau.saboteur.model.CardType
import com.aau.server.game.GameBoard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


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
}