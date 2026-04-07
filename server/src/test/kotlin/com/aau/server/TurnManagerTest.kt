package com.aau.server

import com.aau.shared.game.GameState
import com.aau.shared.game.PlayerTurn
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TurnManagerTest {

    private val playerIds = listOf("Alice", "Bob", "Charlie")
    private lateinit var initialState: SaboteurGameState

    @BeforeEach
    fun setUp() {
        val distribution = CardDistributor.distribute(playerIds)
        val sharedGameState = GameState(
            players = playerIds.mapIndexed { index, id ->
                PlayerTurn(playerId = id, playerName = id, turnOrder = index + 1)
            },
            currentPlayerId = playerIds.first()
        )
        initialState = SaboteurGameState.from(distribution, sharedGameState)
    }

    // --- Helpers ---

    private fun totalCards(state: SaboteurGameState): Int =
        state.hands.values.sumOf { it.size } + state.drawPile.size

    private fun aliceCardId(): String = initialState.hands.getValue("Alice").first().id

    private fun stateWithEmptyDrawPile(): SaboteurGameState =
        initialState.copy(drawPile = mutableListOf())

    // --- playCard ---

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
        assertEquals(initialHandSize, newState.hands.getValue("Alice").size)
    }

    @Test
    fun `playCard does not draw when draw pile is empty`() {
        val state = stateWithEmptyDrawPile()
        val initialHandSize = state.hands.getValue("Alice").size
        val newState = TurnManager.playCard(state, "Alice", state.hands.getValue("Alice").first().id)
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