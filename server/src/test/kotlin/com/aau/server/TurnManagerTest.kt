package com.aau.server

import com.aau.shared.game.GameState
import com.aau.shared.game.PlayerTurn
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TurnManagerTest {

    // Deterministic turn order: Alice=1, Bob=2, Charlie=3
    private val playerIds = listOf("Alice", "Bob", "Charlie")
    private lateinit var initialState: SaboteurGameState

    @BeforeEach
    fun setUp() {
        // 3 players × 6 cards = 18 dealt, draw pile starts with 22 cards
        val distribution = CardDistributor.distribute(playerIds)
        val sharedGameState = GameState(
            players = playerIds.mapIndexed { index, id ->
                PlayerTurn(playerId = id, playerName = id, turnOrder = index + 1)
            },
            currentPlayerId = playerIds.first() // Alice starts
        )
        initialState = SaboteurGameState.from(distribution, sharedGameState)
    }

    // --- Helpers ---

    /**
     * Counts all cards still tracked in [SaboteurGameState] (hands + draw pile).
     * Cards on the board (played) or discarded are NOT included — their count
     * decreases by 1 per playCard/discardCard call.
     */
    private fun totalCards(state: SaboteurGameState): Int =
        state.hands.values.sumOf { it.size } + state.drawPile.size

    private fun aliceCardId(): String = initialState.hands.getValue("Alice").first().id

    // Simulates the end-game scenario where no cards remain to draw
    private fun stateWithEmptyDrawPile(): SaboteurGameState =
        initialState.copy(drawPile = emptyList())

    // --- playCard ---

    /**
     * The played card leaves the player's hand and is placed on the game board.
     * Board placement itself is handled by [GameBoard] — TurnManager only removes
     * the card from the hand. After this call the card no longer appears in the state.
     */
    @Test
    fun `playCard removes card from player's hand`() {
        val cardId = aliceCardId()
        val newState = TurnManager.playCard(initialState, "Alice", cardId)
        assertFalse(newState.hands.getValue("Alice").any { it.id == cardId })
    }

    @Test
    fun `playCard draws replacement when draw pile is non-empty`() {
        val initialHandSize = initialState.hands.getValue("Alice").size
        val newState = TurnManager.playCard(initialState, "Alice", aliceCardId())
        // One card played, one drawn → hand size stays the same
        assertEquals(initialHandSize, newState.hands.getValue("Alice").size)
    }

    @Test
    fun `playCard does not draw when draw pile is empty`() {
        val state = stateWithEmptyDrawPile()
        val initialHandSize = state.hands.getValue("Alice").size
        val newState = TurnManager.playCard(state, "Alice", state.hands.getValue("Alice").first().id)
        // No replacement available → hand shrinks by one
        assertEquals(initialHandSize - 1, newState.hands.getValue("Alice").size)
    }

    @Test
    fun `playCard advances turn to next player`() {
        val newState = TurnManager.playCard(initialState, "Alice", aliceCardId())
        assertEquals("Bob", newState.gameState.currentPlayerId)
    }

    @Test
    fun `playCard throws when wrong player acts`() {
        assertThrows<IllegalArgumentException> {
            TurnManager.playCard(initialState, "Bob", initialState.hands.getValue("Bob").first().id)
        }
    }

    @Test
    fun `playCard throws when card not in hand`() {
        assertThrows<IllegalArgumentException> {
            TurnManager.playCard(initialState, "Alice", "nonexistent_card")
        }
    }

    // --- discardCard ---

    /**
     * The discarded card is permanently removed from the game — it is not tracked
     * in any discard pile. A player discards when no legal board placement exists.
     */
    @Test
    fun `discardCard removes card from player's hand`() {
        val cardId = aliceCardId()
        val newState = TurnManager.discardCard(initialState, "Alice", cardId)
        assertFalse(newState.hands.getValue("Alice").any { it.id == cardId })
    }

    @Test
    fun `discardCard draws replacement when draw pile is non-empty`() {
        val initialHandSize = initialState.hands.getValue("Alice").size
        val newState = TurnManager.discardCard(initialState, "Alice", aliceCardId())
        // One card discarded, one drawn → hand size stays the same
        assertEquals(initialHandSize, newState.hands.getValue("Alice").size)
    }

    @Test
    fun `discardCard advances turn to next player`() {
        val newState = TurnManager.discardCard(initialState, "Alice", aliceCardId())
        assertEquals("Bob", newState.gameState.currentPlayerId)
    }

    @Test
    fun `discardCard does not draw when draw pile is empty`() {
        val state = stateWithEmptyDrawPile()
        val initialHandSize = state.hands.getValue("Alice").size
        val newState = TurnManager.discardCard(state, "Alice", state.hands.getValue("Alice").first().id)
        // No replacement available → hand shrinks by one
        assertEquals(initialHandSize - 1, newState.hands.getValue("Alice").size)
    }

    @Test
    fun `discardCard throws when wrong player acts`() {
        assertThrows<IllegalArgumentException> {
            TurnManager.discardCard(initialState, "Charlie", initialState.hands.getValue("Charlie").first().id)
        }
    }

    // --- drawCard ---

    @Test
    fun `drawCard adds card to player's hand`() {
        val initialHandSize = initialState.hands.getValue("Alice").size
        val newState = TurnManager.drawCard(initialState, "Alice")
        assertEquals(initialHandSize + 1, newState.hands.getValue("Alice").size)
    }

    @Test
    fun `drawCard shrinks draw pile by one`() {
        val initialPileSize = initialState.drawPile.size
        val newState = TurnManager.drawCard(initialState, "Alice")
        assertEquals(initialPileSize - 1, newState.drawPile.size)
    }

    @Test
    fun `drawCard advances turn to next player`() {
        val newState = TurnManager.drawCard(initialState, "Alice")
        assertEquals("Bob", newState.gameState.currentPlayerId)
    }

    @Test
    fun `drawCard throws when draw pile is empty`() {
        assertThrows<IllegalStateException> {
            TurnManager.drawCard(stateWithEmptyDrawPile(), "Alice")
        }
    }

    // --- nextPlayer ---

    @Test
    fun `nextPlayer advances to next player by turn order`() {
        val newState = TurnManager.nextPlayer(initialState)
        assertEquals("Bob", newState.gameState.currentPlayerId)
    }

    @Test
    fun `nextPlayer wraps around from last to first player`() {
        val lastPlayerState = initialState.copy(
            gameState = initialState.gameState.copy(currentPlayerId = "Charlie")
        )
        val newState = TurnManager.nextPlayer(lastPlayerState)
        assertEquals("Alice", newState.gameState.currentPlayerId)
    }

    // --- Card conservation ---

    /**
     * playCard and discardCard each reduce the tracked card count by 1:
     * the card leaves SaboteurGameState (board / discard) and is no longer counted.
     * drawCard is neutral — a card moves from the draw pile to the hand.
     */
    @Test
    fun `playCard reduces tracked card count by one (played card goes to board)`() {
        val before = totalCards(initialState)
        val after = totalCards(TurnManager.playCard(initialState, "Alice", aliceCardId()))
        assertEquals(before - 1, after)
    }

    @Test
    fun `discardCard reduces tracked card count by one (discarded card leaves game)`() {
        val before = totalCards(initialState)
        val after = totalCards(TurnManager.discardCard(initialState, "Alice", aliceCardId()))
        assertEquals(before - 1, after)
    }

    @Test
    fun `drawCard conserves total card count (card moves from pile to hand)`() {
        val before = totalCards(initialState)
        val after = totalCards(TurnManager.drawCard(initialState, "Alice"))
        assertEquals(before, after)
    }

    // --- Immutability ---

    @Test
    fun `playCard does not mutate input state`() {
        val originalHandSize = initialState.hands.getValue("Alice").size
        val originalPileSize = initialState.drawPile.size
        TurnManager.playCard(initialState, "Alice", aliceCardId())
        assertEquals(originalHandSize, initialState.hands.getValue("Alice").size)
        assertEquals(originalPileSize, initialState.drawPile.size)
    }
}