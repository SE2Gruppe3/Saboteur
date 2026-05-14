package com.aau.server

import com.aau.saboteur.model.*
import com.aau.server.game.CardDeck
import com.aau.server.model.CardDistributionResult
import com.aau.server.repository.GameRepository
import com.aau.server.service.GameService
import com.aau.server.service.TurnManager
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock

class TurnManagerTest {

    private lateinit var turnManager: TurnManager
    private lateinit var gameRepository: GameRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var gameService: GameService

    private val lobbyCode = "TEST_LOBBY"
    private val p1 = "player1"
    private val p2 = "player2"
    private val p3 = "player3"

    private val startCard = CardDeck.createStartCard()
    private val startPosition = BoardPosition(row = 4, column = 2)

    private fun pathCard(id: String) = TunnelCard(
        id = id,
        type = CardType.PATH,
        connections = setOf(Direction.TOP, Direction.BOTTOM)
    )

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
        gameRepository = mock(GameRepository::class.java)
        objectMapper = jacksonObjectMapper()
        gameService = GameService()
        turnManager = TurnManager(gameRepository, objectMapper, gameService)
        
        turnManager.initializeGame(
            lobbyCode,
            distribution(
                h1 = listOf(pathCard("c1")),
                h2 = listOf(pathCard("c2")),
                h3 = listOf(pathCard("c3"))
            ),
            baseState()
        )
    }

    @Test
    fun `playCard validMove removesCardFromHandAndPlacesOnBoard`() {
        val position = BoardPosition(row = 3, column = 2)
        val result = turnManager.playCard(lobbyCode, p1, "c1", position, isRotated = false)

        val hand1 = result.updatedHands[p1]!!
        assertFalse(hand1.any { it.id == "c1" })

        val placed = result.updatedGameState.boardPlacements.find { it.position == position }
        assertNotNull(placed)
        assertEquals("c1", placed!!.card.id)
    }

    @Test
    fun `playCard wrongPlayer throwsException`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(lobbyCode, p2, "c2", BoardPosition(row = 3, column = 2), false)
        }
    }

    @Test
    fun `discardCard validDiscard removesCardFromHand`() {
        val result = turnManager.discardCard(lobbyCode, p1, "c1")
        assertFalse(result.updatedHands[p1]!!.any { it.id == "c1" })
    }

    @Test
    fun `drawCard deckNotEmpty addsCardToHand`() {
        turnManager.initializeGame(
            lobbyCode,
            distribution(
                h1 = listOf(pathCard("c1")),
                h2 = listOf(pathCard("c2")),
                h3 = listOf(pathCard("c3")),
                pile = listOf(pathCard("deck1"))
            ),
            baseState()
        )
        val sizeBefore = turnManager.getHands(lobbyCode)[p1]!!.size
        turnManager.discardCard(lobbyCode, p1, "c1")
        val sizeAfter = turnManager.getHands(lobbyCode)[p1]!!.size

        assertEquals(sizeBefore, sizeAfter)
        assertTrue(turnManager.getHands(lobbyCode)[p1]!!.any { it.id == "deck1" })
    }

    @Test
    fun `getValidPositions onlyStartCard pathCard returnsTopAndBottomNeighbors`() {
        val placements = listOf(PlacedTunnelCard(startPosition, startCard))
        val card = pathCard("vp_tb")
        val valid = turnManager.getValidPositions(lobbyCode, card, isRotated = false, placements)

        assertEquals(2, valid.size)
        assertTrue(valid.contains(BoardPosition(row = 3, column = 2)))
        assertTrue(valid.contains(BoardPosition(row = 5, column = 2)))
    }
}
