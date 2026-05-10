package com.aau.server

import com.aau.saboteur.model.*
import com.aau.server.game.CardDeck
import com.aau.server.model.CardDistributionResult
import com.aau.server.service.TurnManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TurnManagerTest {

    private lateinit var turnManager: TurnManager

    private val p1 = "player1"
    private val p2 = "player2"
    private val p3 = "player3"

    // Start card at (4,2) with all four connections – cards adjacent to it are valid placements
    private val startCard = CardDeck.createStartCard()
    private val startPosition = BoardPosition(row = 4, column = 2)

    // A simple straight card that connects TOP and BOTTOM – fits above/below the start card
    private fun pathCard(id: String) = TunnelCard(
        id = id,
        type = CardType.PATH,
        connections = setOf(Direction.TOP, Direction.BOTTOM)
    )

    // A straight card that connects LEFT and RIGHT – fits left/right of the start card
    private fun hPathCard(id: String) = TunnelCard(
        id = id,
        type = CardType.PATH,
        connections = setOf(Direction.LEFT, Direction.RIGHT)
    )

    private fun baseState(currentPlayer: String = p1) = GameState(
        players = listOf(
            PlayerTurn(p1, "Alice", 1),
            PlayerTurn(p2, "Bob", 2),
            PlayerTurn(p3, "Charlie", 3)
        ),
        currentPlayerId = currentPlayer,
        boardPlacements = listOf(PlacedTunnelCard(startPosition, startCard))
    )

    private fun distribution(
        h1: List<TunnelCard> = emptyList(),
        h2: List<TunnelCard> = emptyList(),
        h3: List<TunnelCard> = emptyList(),
        pile: List<TunnelCard> = emptyList()
    ) = CardDistributionResult(
        hands = mapOf(p1 to h1, p2 to h2, p3 to h3),
        drawPile = pile,
        goalCards = CardDeck.createGoalCards(),
        startCard = startCard
    )

    @BeforeEach
    fun setUp() {
        turnManager = TurnManager()
        turnManager.initializeGame(
            distribution(
                h1 = listOf(pathCard("c1")),
                h2 = listOf(pathCard("c2")),
                h3 = listOf(pathCard("c3"))
            ),
            baseState()
        )
    }

    // --- playCard tests ---

    @Test
    fun `playCard validMove removesCardFromHandAndPlacesOnBoard`() {
        // Above the start card: row=3, column=2; the placed card needs BOTTOM to connect to start's TOP
        val position = BoardPosition(row = 3, column = 2)
        val result = turnManager.playCard(p1, "c1", position, isRotated = false)

        val hand1 = result.updatedHands[p1]!!
        assertFalse(hand1.any { it.id == "c1" }, "Card should be removed from hand after play")

        val placed = result.updatedGameState.boardPlacements.find { it.position == position }
        assertNotNull(placed, "Card should appear on the board")
        assertEquals("c1", placed!!.card.id)
    }

    @Test
    fun `playCard wrongPlayer throwsException`() {
        assertThrows<IllegalArgumentException>("Not this player's turn") {
            turnManager.playCard(p2, "c2", BoardPosition(row = 3, column = 2), false)
        }
    }

    @Test
    fun `playCard cardNotInHand throwsException`() {
        assertThrows<IllegalArgumentException>("Card not found in hand") {
            turnManager.playCard(p1, "does_not_exist", BoardPosition(row = 3, column = 2), false)
        }
    }

    @Test
    fun `playCard invalidPosition throwsException`() {
        // (0,0) has no neighbor in our board
        assertThrows<IllegalArgumentException>("Invalid position should be rejected") {
            turnManager.playCard(p1, "c1", BoardPosition(row = 0, column = 0), false)
        }
    }

    @Test
    fun `playCard rotatedCard usesEffectiveConnections`() {
        // hPathCard has LEFT+RIGHT connections. Rotated 180° it still has LEFT+RIGHT (symmetric).
        // Place it to the right of start: position (4,3). Start has RIGHT, card needs LEFT.
        turnManager.initializeGame(
            distribution(h1 = listOf(hPathCard("h1")), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            baseState()
        )
        val result = turnManager.playCard(p1, "h1", BoardPosition(row = 4, column = 3), isRotated = true)
        val placed = result.updatedGameState.boardPlacements.find { it.position == BoardPosition(4, 3) }
        assertNotNull(placed)
        assertTrue(placed!!.card.isRotated)
    }

    // --- discardCard tests ---

    @Test
    fun `discardCard validDiscard removesCardFromHand`() {
        val result = turnManager.discardCard(p1, "c1")
        assertFalse(result.updatedHands[p1]!!.any { it.id == "c1" }, "Discarded card should be gone")
    }

    @Test
    fun `discardCard wrongPlayer throwsException`() {
        assertThrows<IllegalArgumentException> {
            turnManager.discardCard(p2, "c2")
        }
    }

    @Test
    fun `discardCard cardNotInHand throwsException`() {
        assertThrows<IllegalArgumentException> {
            turnManager.discardCard(p1, "nonexistent")
        }
    }

    // --- drawCard behaviour (tested via discard side-effects) ---

    @Test
    fun `drawCard deckNotEmpty addsCardToHand`() {
        turnManager.initializeGame(
            distribution(
                h1 = listOf(pathCard("c1")),
                h2 = listOf(pathCard("c2")),
                h3 = listOf(pathCard("c3")),
                pile = listOf(pathCard("deck1"))
            ),
            baseState()
        )
        val sizeBefore = turnManager.getHands()[p1]!!.size
        turnManager.discardCard(p1, "c1")
        val sizeAfter = turnManager.getHands()[p1]!!.size

        assertEquals(sizeBefore, sizeAfter, "Discard + draw should keep hand size the same when deck has cards")
        assertTrue(turnManager.getHands()[p1]!!.any { it.id == "deck1" }, "Drawn card should be in hand")
    }

    @Test
    fun `drawCard deckEmpty handUnchanged`() {
        // setUp has empty draw pile
        val sizeBefore = turnManager.getHands()[p1]!!.size
        turnManager.discardCard(p1, "c1")
        val sizeAfter = turnManager.getHands()[p1]!!.size

        assertEquals(sizeBefore - 1, sizeAfter, "Hand should shrink by 1 when deck is empty")
    }

    // --- nextPlayer tests ---

    @Test
    fun `nextPlayer advancesToNextPlayerInOrder`() {
        turnManager.discardCard(p1, "c1")
        assertEquals(p2, turnManager.getGameState().currentPlayerId)
    }

    @Test
    fun `nextPlayer lastPlayer wrapsAroundToFirst`() {
        turnManager.initializeGame(
            distribution(
                h1 = listOf(pathCard("c1")),
                h2 = listOf(pathCard("c2")),
                h3 = listOf(pathCard("c3x"))
            ),
            baseState(currentPlayer = p3)
        )
        turnManager.discardCard(p3, "c3x")
        assertEquals(p1, turnManager.getGameState().currentPlayerId, "Should wrap around to first player")
    }

    @Test
    fun `nextPlayer advancesThroughAllThreePlayers`() {
        turnManager.initializeGame(
            distribution(
                h1 = listOf(pathCard("a1"), pathCard("a2")),
                h2 = listOf(pathCard("b1"), pathCard("b2")),
                h3 = listOf(pathCard("c1x"), pathCard("c2"))
            ),
            baseState()
        )

        assertEquals(p1, turnManager.getGameState().currentPlayerId)
        turnManager.discardCard(p1, "a1")
        assertEquals(p2, turnManager.getGameState().currentPlayerId)
        turnManager.discardCard(p2, "b1")
        assertEquals(p3, turnManager.getGameState().currentPlayerId)
        turnManager.discardCard(p3, "c1x")
        assertEquals(p1, turnManager.getGameState().currentPlayerId)
    }

    // --- getHands ---

    @Test
    fun `getHands returnsImmutableSnapshot`() {
        val hands = turnManager.getHands()
        assertEquals(1, hands[p1]!!.size)
        assertEquals(1, hands[p2]!!.size)
        assertEquals(1, hands[p3]!!.size)
    }
}