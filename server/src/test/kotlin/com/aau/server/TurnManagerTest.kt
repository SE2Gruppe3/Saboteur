package com.aau.server

import com.aau.saboteur.model.*
import com.aau.server.game.CardDeck
import com.aau.server.model.CardDistributionResult
import com.aau.server.model.GameEntity
import com.aau.server.repository.GameRepository
import com.aau.server.service.GameService
import com.aau.server.service.TurnManager
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.*

class TurnManagerTest {

    private lateinit var turnManager: TurnManager
    private val gameRepository: GameRepository = mock()
    private val gameService: GameService = mock()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val lobbyCode = "LOBBY_COVERAGE"
    private val p1 = "p1"
    private val p2 = "p2"
    private val p3 = "p3"

    private val startPos = BoardPosition(4, 2)
    private val startCard = CardDeck.createStartCard()

    @BeforeEach
    fun setUp() {
        turnManager = TurnManager(gameRepository, objectMapper, gameService)
        setupStandardGame()
        Mockito.clearInvocations(gameRepository)
    }

    private fun setupStandardGame(pile: MutableList<TunnelCard> = mutableListOf()) {
        val players = listOf(
            PlayerTurn(p1, "Alice", 1),
            PlayerTurn(p2, "Bob", 2),
            PlayerTurn(p3, "Charlie", 3)
        )
        val distribution = CardDistributionResult(
            hands = mapOf(
                p1 to mutableListOf(pathCard("c1", setOf(Direction.LEFT, Direction.RIGHT))),
                p2 to mutableListOf(pathCard("c2", setOf(Direction.TOP, Direction.BOTTOM))),
                p3 to mutableListOf(pathCard("c3", Direction.values().toSet()))
            ),
            drawPile = pile,
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        val initialState = GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard)))
        turnManager.initializeGame(lobbyCode, distribution, initialState)
    }

    private fun pathCard(id: String, conns: Set<Direction>) = TunnelCard(id, CardType.PATH, conns)

    @Test
    fun `playCard successful placement and draw`() {
        setupStandardGame(mutableListOf(pathCard("deck1", setOf(Direction.TOP))))
        Mockito.clearInvocations(gameRepository)
        
        val result = turnManager.playCard(lobbyCode, p1, "c1", BoardPosition(4, 3), false)

        assertEquals(p2, result.updatedGameState.currentPlayerId)
        assertTrue(result.updatedGameState.boardPlacements.any { it.position == BoardPosition(4, 3) })
        assertTrue(result.updatedHands[p1]!!.any { it.id == "deck1" })
        verify(gameRepository, times(1)).save(any())
    }

    @Test
    fun `playCard reveals goal and detects win`() {
        val goldCard = TunnelCard("gold", CardType.GOAL, Direction.values().toSet(), isGoal = true, isRevealed = false)
        val allSides = pathCard("all", Direction.values().toSet())
        
        // Build path: Start(4,2) -> (4,3) -> ... -> (4,9) -> (3,9) -> (3,10) [touches goal 4,10]
        val board = mutableListOf(PlacedTunnelCard(startPos, startCard))
        for (col in 3..9) { board.add(PlacedTunnelCard(BoardPosition(4, col), allSides)) }
        board.add(PlacedTunnelCard(BoardPosition(3, 9), allSides))
        board.add(PlacedTunnelCard(BoardPosition(4, 10), goldCard))
        
        val dist = CardDistributionResult(mapOf(p1 to mutableListOf(allSides)), mutableListOf(), emptyList(), startCard)
        turnManager.initializeGame("WIN", dist, GameState(listOf(PlayerTurn(p1, "A", 1)), p1, board))

        // Place at (3,10). Touching goal (4,10) from ABOVE. Path to start exists via (3,9).
        val result = turnManager.playCard("WIN", p1, "all", BoardPosition(3, 10), false)
        
        val revealed = result.updatedGameState.boardPlacements.find { it.position == BoardPosition(4, 10) }
        assertTrue(revealed!!.card.isRevealed)
        assertEquals("DWARVES", result.winner)
    }

    @Test
    fun `revealGoalCards handles connection mismatch by flipping`() {
        // Goal requires LEFT. Approach from RIGHT side.
        val goalCard = TunnelCard("goal", CardType.GOAL, setOf(Direction.LEFT), isGoal = false, isRevealed = false)
        val all = pathCard("all", Direction.values().toSet())
        val board = mutableListOf(
            PlacedTunnelCard(startPos, startCard),
            // Build path around: (4,2)->(3,2)->(3,3)->(3,4)->(3,5)->(4,5)
            PlacedTunnelCard(BoardPosition(3, 2), all),
            PlacedTunnelCard(BoardPosition(3, 3), all),
            PlacedTunnelCard(BoardPosition(3, 4), all),
            PlacedTunnelCard(BoardPosition(3, 5), all),
            PlacedTunnelCard(BoardPosition(4, 4), goalCard)
        )
        val dist = CardDistributionResult(mapOf(p1 to mutableListOf(all)), mutableListOf(), emptyList(), startCard)
        turnManager.initializeGame("FLIP", dist, GameState(listOf(PlayerTurn(p1, "A", 1)), p1, board))

        // Place at (4,5). ADJACENT to goal(4,4) RIGHT side. Path is reachable.
        // Connection mismatch triggers 180 flip.
        val result = turnManager.playCard("FLIP", p1, "all", BoardPosition(4, 5), false)
        
        val goal = result.updatedGameState.boardPlacements.find { it.position == BoardPosition(4, 4) }
        assertTrue(goal!!.card.isRevealed)
        assertTrue(goal.card.isRotated)
    }

    @Test
    fun `loadFromDb handles recovery and corrupted json`() {
        val players = listOf(PlayerTurn(p1, "Alice", 1))
        val board = listOf(PlacedTunnelCard(startPos, startCard))
        val validEntity = GameEntity("OK", p1, objectMapper.writeValueAsString(board), "[]", "[]", 
            objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1", emptySet())))), objectMapper.writeValueAsString(players), "{}", false, 0)
        
        val badEntity = GameEntity("FAIL", null, "{invalid}", "[]", "[]", "[]", "[]", "{}", false, 0)
        
        whenever(gameRepository.findAll()).thenReturn(listOf(validEntity, badEntity))
        
        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        assertEquals(1, freshManager.loadFromDb())
        assertNotNull(freshManager.getGameStateSnapshot("OK"))
    }

    @Test
    fun `discardCard Saboteur win`() {
        val players = listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2))
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(pathCard("c1", emptySet())), p2 to mutableListOf(pathCard("c2", emptySet()))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("SAB", dist, GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard))))
        
        turnManager.discardCard("SAB", p1, "c1")
        val res = turnManager.discardCard("SAB", p2, "c2")
        
        assertEquals("SABOTEURS", res.winner)
    }

    @Test
    fun `playCard should throw if card not in hand`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(lobbyCode, p1, "ghost", BoardPosition(4,3), false)
        }
    }

    @Test
    fun `removeGame clears state`() {
        turnManager.removeGame(lobbyCode)
        assertThrows<IllegalArgumentException> {
            turnManager.getGameStateSnapshot(lobbyCode)
        }
    }
}
