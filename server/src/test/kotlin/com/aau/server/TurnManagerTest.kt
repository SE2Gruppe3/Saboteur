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

    // --- canPlaceOnBoard: bidirectional connectivity ---

    @Test
    fun `playCard cardConnectsTowardNeighborButNeighborDoesNotConnectBack throwsException`() {
        // Setup: a {TOP, BOTTOM} card at (4,3) – no RIGHT connection.
        // Place hPathCard {LEFT,RIGHT} at (4,4): LEFT→neighbor connects=true, neighbor RIGHT=false → mismatch
        val tbAtRight = TunnelCard("tb_right", CardType.PATH, setOf(Direction.TOP, Direction.BOTTOM))
        val stateWithExtra = baseState().copy(
            boardPlacements = listOf(
                PlacedTunnelCard(startPosition, startCard),
                PlacedTunnelCard(BoardPosition(row = 4, column = 3), tbAtRight)
            )
        )
        val lr = hPathCard("lr_test")
        turnManager.initializeGame(
            distribution(h1 = listOf(lr), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithExtra
        )
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(p1, "lr_test", BoardPosition(row = 4, column = 4), isRotated = false)
        }
    }

    @Test
    fun `playCard noSharedConnectionOnEitherSide throwsException`() {
        // XOR rule: wall/wall (neither side connects) is valid adjacency-wise.
        // Setup: {TOP,BOTTOM} at (4,3) – no RIGHT. Another {TOP,BOTTOM} at (4,4): neither connects at shared edge.
        // Adjacency: false == false → VALID. But (4,3) is unreachable from start (start RIGHT != (4,3) LEFT=false),
        // so reachability fails → exception.
        val tbAtRight = TunnelCard("tb_right2", CardType.PATH, setOf(Direction.TOP, Direction.BOTTOM))
        val stateWithExtra = baseState().copy(
            boardPlacements = listOf(
                PlacedTunnelCard(startPosition, startCard),
                PlacedTunnelCard(BoardPosition(row = 4, column = 3), tbAtRight)
            )
        )
        val tbNew = pathCard("tb_new")
        turnManager.initializeGame(
            distribution(h1 = listOf(tbNew), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithExtra
        )
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(p1, "tb_new", BoardPosition(row = 4, column = 4), isRotated = false)
        }
    }

    @Test
    fun `playCard rotatedCardInvalidAfterFlip throwsException`() {
        // bottomLeftCard {BOTTOM, LEFT} unrotated placed above start (3,2) would be VALID
        // (BOTTOM connects to start's TOP). Rotated → {TOP, RIGHT}: BOTTOM is gone → INVALID.
        val bottomLeftCard = TunnelCard("bl_rot", CardType.PATH, setOf(Direction.BOTTOM, Direction.LEFT))
        turnManager.initializeGame(
            distribution(h1 = listOf(bottomLeftCard), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            baseState()
        )
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(p1, "bl_rot", BoardPosition(row = 3, column = 2), isRotated = true)
        }
    }

    // --- action card placement guard ---

    @Test
    fun `playCard actionCardType throwsException`() {
        val actionCard = TunnelCard("rock1", CardType.ROCKFALL, emptySet())
        turnManager.initializeGame(
            distribution(h1 = listOf(actionCard), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            baseState()
        )
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(p1, "rock1", BoardPosition(row = 3, column = 2), isRotated = false)
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

    // --- XOR adjacency rule: wall/wall valid when reachable via another neighbor ---

    @Test
    fun `playCard wallFacesWall withReachabilityViaOtherNeighbor isAllowed`() {
        // path_all at (4,3): reachable from start (start RIGHT / path_all LEFT both true)
        // wall_above {LEFT,RIGHT} at (3,4): no BOTTOM → wall on TOP edge of (4,4)
        // New card {LEFT} at (4,4):
        //   LEFT  neighbor (4,3): path_all RIGHT=true, card LEFT=true  → both connect ✓ → reachable via (4,3) ✓
        //   TOP   neighbor (3,4): wallAbove BOTTOM=false, card TOP=false → wall/wall XOR=false ✓
        val pathAll  = TunnelCard("all", CardType.PATH, Direction.values().toSet())
        val wallAbove = TunnelCard("wall_above", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        val stateWithSetup = baseState().copy(
            boardPlacements = listOf(
                PlacedTunnelCard(startPosition, startCard),
                PlacedTunnelCard(BoardPosition(row = 4, column = 3), pathAll),
                PlacedTunnelCard(BoardPosition(row = 3, column = 4), wallAbove)
            )
        )
        val card = TunnelCard("left_only", CardType.PATH, setOf(Direction.LEFT))
        turnManager.initializeGame(
            distribution(h1 = listOf(card), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithSetup
        )
        val result = turnManager.playCard(p1, "left_only", BoardPosition(row = 4, column = 4), isRotated = false)
        assertNotNull(result.updatedGameState.boardPlacements.find { it.position == BoardPosition(4, 4) })
    }

    // --- reachability: dead-end cards block the path graph ---

    @Test
    fun `playCard cellOnlyAdjacentToDeadEnd throwsException`() {
        // dead_lr {LEFT,RIGHT} at (4,3): adjacency to start passes, but dead-end is not traversable.
        // hPathCard {LEFT,RIGHT} at (4,4): adjacency to dead-end passes, but reachability from START fails.
        val deadEnd = TunnelCard("dead_lr", CardType.DEAD_END, setOf(Direction.LEFT, Direction.RIGHT))
        val stateWithDeadEnd = baseState().copy(
            boardPlacements = listOf(
                PlacedTunnelCard(startPosition, startCard),
                PlacedTunnelCard(BoardPosition(row = 4, column = 3), deadEnd)
            )
        )
        val lr = hPathCard("lr_after_dead")
        turnManager.initializeGame(
            distribution(h1 = listOf(lr), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithDeadEnd
        )
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(p1, "lr_after_dead", BoardPosition(row = 4, column = 4), isRotated = false)
        }
    }

    @Test
    fun `playCard cellReachableViaPathCards isAllowed`() {
        // hPathCard {LEFT,RIGHT} at (4,3): reachable from start via PATH.
        // Another hPathCard {LEFT,RIGHT} at (4,4): adjacent to reachable PATH card → valid placement.
        val pathAtRight = hPathCard("path_at_right")
        val stateWithPath = baseState().copy(
            boardPlacements = listOf(
                PlacedTunnelCard(startPosition, startCard),
                PlacedTunnelCard(BoardPosition(row = 4, column = 3), pathAtRight)
            )
        )
        val lr = hPathCard("lr_after_path")
        turnManager.initializeGame(
            distribution(h1 = listOf(lr), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithPath
        )
        val result = turnManager.playCard(p1, "lr_after_path", BoardPosition(row = 4, column = 4), isRotated = false)
        assertNotNull(result.updatedGameState.boardPlacements.find { it.position == BoardPosition(4, 4) })
    }

    // --- nextPlayerId: currentPlayerId not found in players → fallback to first ---

    // ── goal reveal ───────────────────────────────────────────────────────────

    // Goal at (4,4) – one step to the right of the start-adjacent cell (4,3)
    private val goalPos = BoardPosition(row = 4, column = 4)
    private val placePos = BoardPosition(row = 4, column = 3)  // between start and goal

    private fun goalStone(id: String, connections: Set<Direction>) = TunnelCard(
        id = id, type = CardType.GOAL, connections = connections, isRevealed = false, isGoal = false
    )

    private fun goalGold() = TunnelCard(
        id = "goal_gold", type = CardType.GOAL,
        connections = setOf(Direction.TOP, Direction.RIGHT, Direction.BOTTOM, Direction.LEFT),
        isRevealed = false, isGoal = true
    )

    private fun stateWithGoal(goal: TunnelCard, gPos: BoardPosition = goalPos, currentPlayer: String = p1) = GameState(
        players = listOf(PlayerTurn(p1, "Alice", 1), PlayerTurn(p2, "Bob", 2), PlayerTurn(p3, "Charlie", 3)),
        currentPlayerId = currentPlayer,
        boardPlacements = listOf(
            PlacedTunnelCard(startPosition, startCard),
            PlacedTunnelCard(gPos, goal)
        )
    )

    @Test
    fun `playCard pathConnectsTowardGoalThatConnectsBack revealsWithoutRotation`() {
        // goal has LEFT → goalConnects = true → reveal only
        val goal = goalStone("g1", setOf(Direction.LEFT, Direction.BOTTOM))
        turnManager.initializeGame(
            distribution(h1 = listOf(hPathCard("h1")), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithGoal(goal)
        )
        val result = turnManager.playCard(p1, "h1", placePos, isRotated = false)
        val updated = result.updatedGameState.boardPlacements.find { it.position == goalPos }!!
        assertTrue(updated.card.isRevealed)
        assertFalse(updated.card.isRotated)
        assertEquals(setOf(Direction.LEFT, Direction.BOTTOM), updated.card.connections)
    }

    @Test
    fun `playCard pathConnectsTowardGoalThatDoesNotConnectBack revealsAndRotates180`() {
        // goal has {TOP, RIGHT} – no LEFT → goalConnects = false → reveal + rotate
        val goal = goalStone("g1", setOf(Direction.TOP, Direction.RIGHT))
        turnManager.initializeGame(
            distribution(h1 = listOf(hPathCard("h1")), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithGoal(goal)
        )
        val result = turnManager.playCard(p1, "h1", placePos, isRotated = false)
        val updated = result.updatedGameState.boardPlacements.find { it.position == goalPos }!!
        assertTrue(updated.card.isRevealed)
        assertTrue(updated.card.isRotated)
        assertEquals(setOf(Direction.BOTTOM, Direction.LEFT), updated.card.connections)
    }

    @Test
    fun `playCard pathDoesNotConnectTowardGoal goalStaysHidden`() {
        // leftOnlyCard has only LEFT – connects to start but not RIGHT toward goal
        val goal = goalStone("g1", setOf(Direction.LEFT, Direction.BOTTOM))
        val leftOnly = TunnelCard("lft", CardType.PATH, setOf(Direction.LEFT))
        turnManager.initializeGame(
            distribution(h1 = listOf(leftOnly), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithGoal(goal)
        )
        val result = turnManager.playCard(p1, "lft", placePos, isRotated = false)
        val unchanged = result.updatedGameState.boardPlacements.find { it.position == goalPos }!!
        assertFalse(unchanged.card.isRevealed)
    }

    @Test
    fun `playCard pathAdjacentToGoalGold revealsWithoutRotation`() {
        // goal_gold connects all four directions → goalConnects always true → never rotated
        turnManager.initializeGame(
            distribution(h1 = listOf(hPathCard("h1")), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithGoal(goalGold())
        )
        val result = turnManager.playCard(p1, "h1", placePos, isRotated = false)
        val updated = result.updatedGameState.boardPlacements.find { it.position == goalPos }!!
        assertTrue(updated.card.isRevealed)
        assertFalse(updated.card.isRotated)
    }

    @Test
    fun `playCard pathAdjacentToAlreadyRevealedGoal doesNotReprocess`() {
        val alreadyRevealed = TunnelCard(
            "g1", CardType.GOAL, setOf(Direction.LEFT, Direction.BOTTOM), isRevealed = true, isGoal = false
        )
        turnManager.initializeGame(
            distribution(h1 = listOf(hPathCard("h1")), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithGoal(alreadyRevealed)
        )
        val result = turnManager.playCard(p1, "h1", placePos, isRotated = false)
        val unchanged = result.updatedGameState.boardPlacements.find { it.position == goalPos }!!
        assertTrue(unchanged.card.isRevealed)
        assertFalse(unchanged.card.isRotated)
        assertEquals(setOf(Direction.LEFT, Direction.BOTTOM), unchanged.card.connections)
    }

    @Test
    fun `playCard pathAdjacentToMultipleGoals revealsAll`() {
        // goal1 to the RIGHT of placePos, goal2 BELOW – placed card connects both directions
        val pos1 = BoardPosition(row = 4, column = 4)
        val pos2 = BoardPosition(row = 5, column = 3)
        val goal1 = goalStone("g1", setOf(Direction.LEFT, Direction.TOP))   // LEFT → goalConnects RIGHT side
        val goal2 = goalStone("g2", setOf(Direction.TOP, Direction.RIGHT))  // TOP → goalConnects BOTTOM side
        val boardState = GameState(
            players = listOf(PlayerTurn(p1, "Alice", 1), PlayerTurn(p2, "Bob", 2), PlayerTurn(p3, "Charlie", 3)),
            currentPlayerId = p1,
            boardPlacements = listOf(
                PlacedTunnelCard(startPosition, startCard),
                PlacedTunnelCard(pos1, goal1),
                PlacedTunnelCard(pos2, goal2)
            )
        )
        val multiCard = TunnelCard("mc", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT, Direction.BOTTOM))
        turnManager.initializeGame(
            distribution(h1 = listOf(multiCard), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            boardState
        )
        val result = turnManager.playCard(p1, "mc", placePos, isRotated = false)
        assertTrue(result.updatedGameState.boardPlacements.find { it.position == pos1 }!!.card.isRevealed)
        assertTrue(result.updatedGameState.boardPlacements.find { it.position == pos2 }!!.card.isRevealed)
    }

    @Test
    fun `playCard deadEndAdjacentToGoal doesNotRevealGoal`() {
        val goal = goalStone("g1", setOf(Direction.LEFT, Direction.BOTTOM))
        val deadEnd = TunnelCard("de1", CardType.DEAD_END, setOf(Direction.LEFT, Direction.RIGHT))
        turnManager.initializeGame(
            distribution(h1 = listOf(deadEnd), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithGoal(goal)
        )
        val result = turnManager.playCard(p1, "de1", placePos, isRotated = false)
        assertFalse(result.updatedGameState.boardPlacements.find { it.position == goalPos }!!.card.isRevealed)
    }

    // ── revealed goal as neighbour – effective-connections contract ───────────
    //
    // Connections are always stored as the effective (post-rotation) value.
    // isRotated is a rendering hint only – the validator must NOT re-apply the
    // rotation; it reads .connections directly and gets the right answer.

    // Goal at (3,3) – placed card goes BELOW it at (4,3) so dir=TOP in the check
    private val aboveGoalPos = BoardPosition(row = 3, column = 3)

    // ── reachability through revealed goal ────────────────────────────────────

    @Test
    fun `isReachableFromStart revealedGoalIsTraversed placedBeyondGoalIsValid`() {
        // PATH at (4,3) connects to revealed goal at (4,4) {LEFT,BOTTOM}.
        // The ONLY path to (5,4) goes through the goal's BOTTOM connection.
        // This test fails if goal cards are excluded from the BFS.
        val pathBetween = TunnelCard("pb", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        val revealedGoal = TunnelCard("g1", CardType.GOAL, setOf(Direction.LEFT, Direction.BOTTOM), isRevealed = true)
        val boardState = GameState(
            players = listOf(PlayerTurn(p1, "Alice", 1), PlayerTurn(p2, "Bob", 2), PlayerTurn(p3, "Charlie", 3)),
            currentPlayerId = p1,
            boardPlacements = listOf(
                PlacedTunnelCard(startPosition, startCard),
                PlacedTunnelCard(BoardPosition(row = 4, column = 3), pathBetween),
                PlacedTunnelCard(BoardPosition(row = 4, column = 4), revealedGoal)
            )
        )
        val topCard = TunnelCard("tc", CardType.PATH, setOf(Direction.TOP))
        turnManager.initializeGame(
            distribution(h1 = listOf(topCard), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            boardState
        )
        val result = turnManager.playCard(p1, "tc", BoardPosition(row = 5, column = 4), isRotated = false)
        assertNotNull(result.updatedGameState.boardPlacements.find { it.position == BoardPosition(5, 4) })
    }

    @Test
    fun `isReachableFromStart unrevealedGoalNotTraversed placedBeyondGoalIsInvalid`() {
        // Same layout but the goal is unrevealed — BFS must not traverse it.
        val pathBetween = TunnelCard("pb", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        val unrevealedGoal = TunnelCard("g1", CardType.GOAL, setOf(Direction.LEFT, Direction.BOTTOM), isRevealed = false)
        val boardState = GameState(
            players = listOf(PlayerTurn(p1, "Alice", 1), PlayerTurn(p2, "Bob", 2), PlayerTurn(p3, "Charlie", 3)),
            currentPlayerId = p1,
            boardPlacements = listOf(
                PlacedTunnelCard(startPosition, startCard),
                PlacedTunnelCard(BoardPosition(row = 4, column = 3), pathBetween),
                PlacedTunnelCard(BoardPosition(row = 4, column = 4), unrevealedGoal)
            )
        )
        // {LEFT} has no RIGHT toward goal → wall/wall adjacency passes; (5,4) blocked by reachability
        val leftOnly = TunnelCard("lo", CardType.PATH, setOf(Direction.LEFT))
        turnManager.initializeGame(
            distribution(h1 = listOf(leftOnly), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            boardState
        )
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(p1, "lo", BoardPosition(row = 5, column = 4), isRotated = false)
        }
    }

    @Test
    fun `canPlaceOnBoard revealedGoalConnectsBack isValid`() {
        // goal {LEFT, BOTTOM} revealed at (4,4). hPathCard RIGHT connects to goal LEFT → match → VALID
        val revealed = TunnelCard("g1", CardType.GOAL, setOf(Direction.LEFT, Direction.BOTTOM), isRevealed = true)
        turnManager.initializeGame(
            distribution(h1 = listOf(hPathCard("h1")), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithGoal(revealed)
        )
        val result = turnManager.playCard(p1, "h1", placePos, isRotated = false)
        assertNotNull(result.updatedGameState.boardPlacements.find { it.position == placePos })
    }

    @Test
    fun `canPlaceOnBoard revealedRotatedGoalStoredConnectionsUsed throwsException`() {
        // Stored (effective) connections after rotation: {RIGHT, TOP} – no LEFT.
        // hPathCard RIGHT connects toward goal, goal has no LEFT → mismatch → INVALID.
        // If the validator incorrectly re-applied rotation it would read {LEFT, BOTTOM} and
        // incorrectly pass.
        val rotated = TunnelCard("g1", CardType.GOAL, setOf(Direction.RIGHT, Direction.TOP),
            isRevealed = true, isRotated = true)
        turnManager.initializeGame(
            distribution(h1 = listOf(hPathCard("h1")), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithGoal(rotated)
        )
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(p1, "h1", placePos, isRotated = false)
        }
    }

    @Test
    fun `canPlaceOnBoard revealedRotatedGoalWallFacesWall isValid`() {
        // Rotated goal {RIGHT, TOP} at (4,4): no LEFT. Card {LEFT} has no RIGHT → wall/wall → VALID.
        val rotated = TunnelCard("g1", CardType.GOAL, setOf(Direction.RIGHT, Direction.TOP),
            isRevealed = true, isRotated = true)
        val leftOnly = TunnelCard("lft2", CardType.PATH, setOf(Direction.LEFT))
        turnManager.initializeGame(
            distribution(h1 = listOf(leftOnly), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithGoal(rotated)
        )
        val result = turnManager.playCard(p1, "lft2", placePos, isRotated = false)
        assertNotNull(result.updatedGameState.boardPlacements.find { it.position == placePos })
    }

    @Test
    fun `canPlaceOnBoard revealedGoalGoldAllDirections isAlwaysValid`() {
        // goal_gold connects all four directions → neighborConnects always true → VALID for any card connecting toward it
        val gold = TunnelCard("goal_gold", CardType.GOAL,
            setOf(Direction.TOP, Direction.RIGHT, Direction.BOTTOM, Direction.LEFT),
            isRevealed = true, isGoal = true)
        turnManager.initializeGame(
            distribution(h1 = listOf(hPathCard("h1")), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithGoal(gold)
        )
        val result = turnManager.playCard(p1, "h1", placePos, isRotated = false)
        assertNotNull(result.updatedGameState.boardPlacements.find { it.position == placePos })
    }

    @Test
    fun `canPlaceOnBoard goalHasBottomConnection placedBelowIsValid`() {
        // Goal {BOTTOM, RIGHT} at (3,3). Card {LEFT, TOP} placed at (4,3): TOP toward goal,
        // goal BOTTOM connects back → match → VALID.
        val goal = TunnelCard("g1", CardType.GOAL, setOf(Direction.BOTTOM, Direction.RIGHT), isRevealed = true)
        val topCard = TunnelCard("tp1", CardType.PATH, setOf(Direction.LEFT, Direction.TOP))
        turnManager.initializeGame(
            distribution(h1 = listOf(topCard), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithGoal(goal, aboveGoalPos)
        )
        val result = turnManager.playCard(p1, "tp1", placePos, isRotated = false)
        assertNotNull(result.updatedGameState.boardPlacements.find { it.position == placePos })
    }

    @Test
    fun `canPlaceOnBoard rotatedGoalBottomRemoved placedBelowThrowsException`() {
        // Goal was {BOTTOM, RIGHT}; after 180° rotation stored as {TOP, LEFT} – no BOTTOM.
        // Card {LEFT, TOP} at (4,3): TOP toward goal, goal has no BOTTOM → mismatch → INVALID.
        val rotated = TunnelCard("g1", CardType.GOAL, setOf(Direction.TOP, Direction.LEFT),
            isRevealed = true, isRotated = true)
        val topCard = TunnelCard("tp2", CardType.PATH, setOf(Direction.LEFT, Direction.TOP))
        turnManager.initializeGame(
            distribution(h1 = listOf(topCard), h2 = listOf(pathCard("c2")), h3 = listOf(pathCard("c3"))),
            stateWithGoal(rotated, aboveGoalPos)
        )
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(p1, "tp2", placePos, isRotated = false)
        }
    }

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