package com.aau.server

import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.TunnelCard
import com.aau.server.game.GameBoard
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

    @Test
    fun `cannot place card when neighbor has no connection toward it (reachability blocked)`() {
        val b = board()
        // first {LEFT} at (1,0): no RIGHT connection, so BFS cannot propagate reachability beyond it.
        // XOR rule: (1,0) RIGHT=false, second LEFT=false → wall/wall, adjacency VALID.
        // Fails only because (2,0) is not reachable from start (no PATH chain through (1,0)'s RIGHT).
        val first = TunnelCard("t11", CardType.PATH, setOf(Direction.LEFT))
        b.placeCard(1, 0, first)
        val second = TunnelCard("t12", CardType.PATH, setOf(Direction.RIGHT))
        assertFalse(b.canPlaceCard(2, 0, second))
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
        // Reuse incompatible direction: card has NO left connection → fails
        val card = TunnelCard("t6", CardType.DEAD_END, setOf(Direction.TOP))
        assertFalse(board().canPlaceCard(1, 0, card))
    }

    @Test
    fun `cannot place card when it connects toward neighbor but neighbor does not connect back`() {
        val b = board()
        // Place a card at (1,0) with only LEFT connection (no RIGHT)
        val noRight = TunnelCard("t13", CardType.PATH, setOf(Direction.LEFT))
        b.placeCard(1, 0, noRight)
        // Card at (2,0) has LEFT connection → cardConnects=true, neighborConnects(RIGHT in noRight)=false → mismatch
        val withLeft = TunnelCard("t14", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        assertFalse(b.canPlaceCard(2, 0, withLeft))
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

    // ── XOR adjacency rule: all four direction/connection combinations ────────

    @Test
    fun `xor rule placed has LEFT neighbor has RIGHT both connect valid`() {
        // Both sides open → tunnel passes through → VALID
        val card = TunnelCard("lr", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        assertTrue(board().canPlaceCard(1, 0, card))
    }

    @Test
    fun `xor rule placed no LEFT neighbor has RIGHT open tunnel invalid`() {
        // start RIGHT=true, card LEFT=false → XOR=true → INVALID
        val card = TunnelCard("tb", CardType.PATH, setOf(Direction.TOP, Direction.BOTTOM))
        assertFalse(board().canPlaceCard(1, 0, card))
    }

    @Test
    fun `xor rule placed has LEFT neighbor no RIGHT open tunnel invalid`() {
        val b = board()
        // noRight {TOP,BOTTOM} at (1,0): no RIGHT. card {LEFT,RIGHT} at (2,0): card LEFT=true, noRight RIGHT=false → XOR=true → INVALID
        b.placeCard(1, 0, TunnelCard("nr", CardType.PATH, setOf(Direction.TOP, Direction.BOTTOM)))
        val card = TunnelCard("lr", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        assertFalse(b.canPlaceCard(2, 0, card))
    }

    @Test
    fun `xor rule wall faces wall neither connects valid by adjacency reachable via other neighbor`() {
        // Layout: start(0,0) → pathAll(1,0) → pathAll(2,0) [reachable]
        //                        ↓
        //                    pathTB(1,1) [has TOP+BOTTOM only, no RIGHT]
        // Target (2,1):
        //   TOP  neighbor (2,0) pathAll: BOTTOM=true,  card TOP=true   → both connect ✓ → reachable via (2,0) ✓
        //   LEFT neighbor (1,1) pathTB:  RIGHT=false, card LEFT=false  → wall/wall XOR=false ✓
        val b = board()
        val allDirs = Direction.values().toSet()
        b.placeCard(1, 0, TunnelCard("a", CardType.PATH, allDirs))
        b.placeCard(2, 0, TunnelCard("b", CardType.PATH, allDirs))
        b.placeCard(1, 1, TunnelCard("c", CardType.PATH, setOf(Direction.TOP, Direction.BOTTOM)))
        val card = TunnelCard("target", CardType.PATH, setOf(Direction.TOP))
        assertTrue(b.canPlaceCard(2, 1, card))
    }

    // ── Reachability via PATH cards only ─────────────────────────────────────

    @Test
    fun `cannot place card adjacent only to dead-end even though adjacency matches`() {
        val b = board()
        // dead_lr {LEFT,RIGHT} at (1,0): adjacency to start OK, but dead-end is not traversable
        val deadEnd = TunnelCard("de1", CardType.DEAD_END, setOf(Direction.LEFT, Direction.RIGHT))
        b.placeCard(1, 0, deadEnd)
        // Card at (2,0) with {LEFT,RIGHT}: adjacency to dead-end passes, but reachability fails
        val pathCard = TunnelCard("p_after_dead", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        assertFalse(b.canPlaceCard(2, 0, pathCard))
    }

    @Test
    fun `can place card adjacent to reachable PATH card`() {
        val b = board()
        // path_lr {LEFT,RIGHT} at (1,0): reachable from start via PATH
        val first = TunnelCard("p1", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        b.placeCard(1, 0, first)
        // Card at (2,0) with {LEFT,RIGHT}: adjacent to reachable PATH card → valid
        val second = TunnelCard("p2", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        assertTrue(b.canPlaceCard(2, 0, second))
    }

}
