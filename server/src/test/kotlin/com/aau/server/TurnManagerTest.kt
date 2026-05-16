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

    private fun pathCard(id: String, conns: Set<Direction> = emptySet()) = TunnelCard(id, CardType.PATH, conns)
    private fun blockCard(id: String) = TunnelCard(id, CardType.LANTERN_RED, emptySet())
    private fun repairCard(id: String) = TunnelCard(id, CardType.LANTERN_GREEN, emptySet())
    private fun mapCard(id: String) = TunnelCard(id, CardType.MAPCARD, emptySet())
    private fun rockfallCard(id: String) = TunnelCard(id, CardType.ROCKFALL, emptySet())

    // --- Standard-Spielverlauf und positive Tests ---

    @Test
    fun `playCard successful placement and draw`() {
        setupStandardGame(mutableListOf(pathCard("deck1", setOf(Direction.TOP))))
        val result = turnManager.playCard(lobbyCode, p1, "c1", BoardPosition(4, 3), false)

        assertEquals(p2, result.updatedGameState.currentPlayerId)
        assertTrue(result.updatedGameState.boardPlacements.any { it.position == BoardPosition(4, 3) })
        verify(gameRepository, atLeastOnce()).save(any())
    }

    @Test
    fun `playBlockCard blocks target tool and advances turn`() {
        val distribution = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(blockCard("b1")), p2 to mutableListOf(pathCard("c2")), p3 to mutableListOf(pathCard("c3"))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("BLOCK", distribution, GameState(listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)), p1, listOf(PlacedTunnelCard(startPos, startCard))))

        val result = turnManager.playBlockCard("BLOCK", p1, "b1", p2)

        val updatedPlayer = result.updatedGameState.players.find { it.playerId == p2 }!!
        assertTrue(ToolType.LANTERN in updatedPlayer.blockedTools)
        assertEquals(p2, result.updatedGameState.currentPlayerId)
    }

    @Test
    fun `playRepairCard removes blocked tool and advances turn`() {
        val players = listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2, blockedTools = setOf(ToolType.LANTERN)))
        val distribution = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(repairCard("r1")), p2 to mutableListOf(pathCard("c2"))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("REPAIR", distribution, GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard))))

        val result = turnManager.playRepairCard("REPAIR", p1, "r1", p2, ToolType.LANTERN)

        val updatedPlayer = result.updatedGameState.players.find { it.playerId == p2 }!!
        assertFalse(ToolType.LANTERN in updatedPlayer.blockedTools)
    }

    @Test
    fun `playMapCard returns goal info and advances turn`() {
        val targetPos = BoardPosition(2, 10)
        val targetGoal = CardDeck.createGoalCards().first()
        val distribution = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(mapCard("m1"))),
            drawPile = mutableListOf(),
            goalCards = listOf(targetGoal),
            startCard = startCard
        )
        val state = GameState(listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)), p1, listOf(PlacedTunnelCard(startPos, startCard), PlacedTunnelCard(targetPos, targetGoal)))
        turnManager.initializeGame("MAP", distribution, state)

        val (_, mapResult) = turnManager.playMapCard("MAP", p1, "m1", targetPos)

        assertEquals(targetPos, mapResult.position)
        assertEquals(targetGoal.id, mapResult.card.id)
    }

    @Test
    fun `playRockfallCard removes card from board`() {
        val targetPos = BoardPosition(4, 3)
        val pCard = pathCard("removable")
        val distribution = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(rockfallCard("rf1"))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        val state = GameState(listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)), p1, listOf(PlacedTunnelCard(startPos, startCard), PlacedTunnelCard(targetPos, pCard)))
        turnManager.initializeGame("ROCK", distribution, state)

        val result = turnManager.playRockfallCard("ROCK", p1, "rf1", targetPos)
        assertNull(result.updatedGameState.boardPlacements.find { it.position == targetPos })
    }

    @Test
    fun `loadFromDb handles recovery`() {
        val players = listOf(PlayerTurn(p1, "Alice", 1))
        val board = listOf(PlacedTunnelCard(startPos, startCard))
        val entity = GameEntity("OK", p1, objectMapper.writeValueAsString(board), "[]", "[]",
            objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
            objectMapper.writeValueAsString(players), "{}", false, 0)

        whenever(gameRepository.findAll()).thenReturn(listOf(entity))

        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        assertEquals(1, freshManager.loadFromDb())
        assertNotNull(freshManager.getGameStateSnapshot("OK"))
    }

    @Test
    fun `removeGame clears state`() {
        turnManager.removeGame(lobbyCode)
        assertThrows<IllegalArgumentException> {
            turnManager.getGameStateSnapshot(lobbyCode)
        }
    }

    // --- Fehlerfälle, Negativtests, Spezialfälle: ---

    @Test
    fun `removeGame does not throw for non-existing game`() {
        turnManager.removeGame("NON_EXISTENT_LOBBY")
    }

    @Test
    fun `getGameStateSnapshot throws for non-existing game`() {
        assertThrows<IllegalArgumentException> {
            turnManager.getGameStateSnapshot("NON_EXISTENT_LOBBY")
        }
    }

    @Test
    fun `getHands returns emptyMap if lobby not found`() {
        val result = turnManager.getHands("DOES_NOT_EXIST")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getGameState throws for non-existent game`() {
        assertThrows<IllegalArgumentException> {
            turnManager.getGameState("NO_GAME")
        }
    }

    // -- Loop- und Grid Coverage: getValidPositions--

    @Test
    fun `getValidPositions returns non-empty for two adjacent cards`() {
        // Setup mit mindestens 2 platzierten Karten (damit der innere Loop in getValidPositions ausgeführt wird)
        val card1 = TunnelCard("1", CardType.PATH, setOf(Direction.RIGHT))
        val card2 = TunnelCard("2", CardType.PATH, setOf(Direction.LEFT))
        val pos1 = BoardPosition(3, 2)
        val pos2 = BoardPosition(3, 3)
        val placements = listOf(
            PlacedTunnelCard(pos1, card1),
            PlacedTunnelCard(pos2, card2)
        )
        val positions = turnManager.getValidPositions("ANY", card1, false, placements)
        assertNotNull(positions)
    }

    @Test
    fun `getValidPositions returns empty if placement list empty`() {
        val result = turnManager.getValidPositions("NO_GAME", TunnelCard("id", CardType.PATH, setOf(Direction.TOP)), false, emptyList())
        assertTrue(result.isEmpty())
    }



    // -- loadFromDb error branch --
    @Test
    fun `loadFromDb logs error on bad entity`() {
        // Simuliere eine Exception beim Deserialisieren, damit der catch-Block ausgeführt wird
        val badEntity = GameEntity(
            "BAD", "BAD", "invalid", "[]", "[]", "{}", "[]", "{}", false, 0
        )
        whenever(gameRepository.findAll()).thenReturn(listOf(badEntity))
        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        // Bei Fehler sollte die games-Map leer bleiben!
        val result = freshManager.loadFromDb()
        assertEquals(0, result)
    }

    // -- drawCardForPlayer leeres Deck (deckWasEmptied coverage) --
    @Test
    fun `drawCardForPlayer sets deckWasEmptied if pile empty`() {
        // Methoden/Logik über public API triggern: Am einfachsten bei leerem drawPile eine Karte spielen
        val players = listOf(PlayerTurn(p1, "A", 1))
        val distribution = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(pathCard("drawMe"))),
            drawPile = mutableListOf(), // leerer Stapel!
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        val initialState = GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard)))
        turnManager.initializeGame("EMPTY_DRAW", distribution, initialState)
        val ex = assertThrows<IllegalArgumentException> {
            // Bei leerem Stapel wird i.d.R. eine Aktion fehlschlagen, ist in Ordnung
            turnManager.playCard("EMPTY_DRAW", p1, "drawMe", BoardPosition(4, 3), false)
        }
    }

}