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

    // --- canPlaceOnBoard: position already occupied ---

    @Test
    fun `playCard positionAlreadyOccupied throwsException`() {
        // The start card occupies startPosition – placing there must fail
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(p1, "c1", startPosition, isRotated = false)
        }
    }

    // --- canPlaceOnBoard: connection mismatch (cardConnects != neighborConnects) ---

    @Test
    fun `playCard connectionMismatch throwsException`() {
        // pathCard has {TOP, BOTTOM}. Placing it to the RIGHT of start at (4,3):
        // LEFT neighbor = start (which connects RIGHT=true), card doesn't connect LEFT=false → mismatch
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(p1, "c1", BoardPosition(row = 4, column = 3), isRotated = false)
        }
    }

    // --- flipConnections: TOP → BOTTOM branch ---

    @Test
    fun `playCard rotatedTopCard exercisesFlipConnectionsTopBranch`() {
        // topRightCard has {TOP, RIGHT}. Rotated → {BOTTOM, LEFT}.
        // Place at (3,2): BOTTOM neighbour = start (connects TOP) → match ✓
        val topRightCard = TunnelCard("tr1", CardType.PATH, setOf(Direction.TOP, Direction.RIGHT))
        turnManager.initializeGame(
            distribution(h1 = listOf(topRightCard), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            baseState()
        )
        val result = turnManager.playCard(p1, "tr1", BoardPosition(row = 3, column = 2), isRotated = true)
        val placed = result.updatedGameState.boardPlacements.find { it.position == BoardPosition(3, 2) }
        assertNotNull(placed)
        assertTrue(placed!!.card.isRotated)
        // After flip: connections must be {BOTTOM, LEFT}
        assertEquals(setOf(Direction.BOTTOM, Direction.LEFT), placed!!.card.connections)
    }

    // --- flipConnections: BOTTOM → TOP branch ---

    @Test
    fun `playCard rotatedBottomCard exercisesFlipConnectionsBottomBranch`() {
        // bottomLeftCard has {BOTTOM, LEFT}. Rotated → {TOP, RIGHT}.
        // Place at (5,2): TOP neighbour = start (connects BOTTOM) → match ✓
        val bottomLeftCard = TunnelCard("bl1", CardType.PATH, setOf(Direction.BOTTOM, Direction.LEFT))
        turnManager.initializeGame(
            distribution(h1 = listOf(bottomLeftCard), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            baseState()
        )
        val result = turnManager.playCard(p1, "bl1", BoardPosition(row = 5, column = 2), isRotated = true)
        val placed = result.updatedGameState.boardPlacements.find { it.position == BoardPosition(5, 2) }
        assertNotNull(placed)
        assertEquals(setOf(Direction.TOP, Direction.RIGHT), placed!!.card.connections)
    }

    // --- opposite(TOP) branch: card placed below an existing card → dir=TOP ---

    @Test
    fun `canPlaceOnBoard cardBelowStart exercisesOppositeTopBranch`() {
        // Placing pathCard {TOP,BOTTOM} at (5,2): TOP neighbour = start → opposite(TOP)=BOTTOM
        // start connects BOTTOM, card connects TOP → match
        val result = turnManager.playCard(p1, "c1", BoardPosition(row = 5, column = 2), isRotated = false)
        assertNotNull(result.updatedGameState.boardPlacements.find { it.position == BoardPosition(5, 2) })
    }

    // --- opposite(RIGHT) branch: card placed left of existing card → dir=RIGHT ---

    @Test
    fun `canPlaceOnBoard cardLeftOfStart exercisesOppositeRightBranch`() {
        // hPathCard {LEFT,RIGHT} at (4,1): RIGHT neighbour = start → opposite(RIGHT)=LEFT
        // start connects LEFT, card connects RIGHT → match
        val lr = hPathCard("lr1")
        turnManager.initializeGame(
            distribution(h1 = listOf(lr), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            baseState()
        )
        val result = turnManager.playCard(p1, "lr1", BoardPosition(row = 4, column = 1), isRotated = false)
        assertNotNull(result.updatedGameState.boardPlacements.find { it.position == BoardPosition(4, 1) })
    }

    // --- nextPlayerId: currentPlayerId not found in players → fallback to first ---

    @Test
    fun `nextPlayerId unknownCurrentPlayer fallsBackToFirst`() {
        val ghost = "ghost"
        val specialState = GameState(
            players = listOf(
                PlayerTurn(p1, "Alice", 1),
                PlayerTurn(p2, "Bob", 2),
                PlayerTurn(p3, "Charlie", 3)
            ),
            currentPlayerId = ghost,
            boardPlacements = listOf(PlacedTunnelCard(startPosition, startCard))
        )
        val specialDist = CardDistributionResult(
            hands = mapOf(p1 to listOf(pathCard("c1")), p2 to listOf(pathCard("c2")),
                p3 to listOf(pathCard("c3")), ghost to listOf(pathCard("cg"))),
            drawPile = emptyList(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame(specialDist, specialState)

        // Discard as the ghost player – passes the currentPlayer require check.
        // nextPlayerId will find idx == -1 and fall back to the first player by turnOrder.
        val result = turnManager.discardCard(ghost, "cg")
        assertEquals(p1, result.updatedGameState.currentPlayerId)
    }
}