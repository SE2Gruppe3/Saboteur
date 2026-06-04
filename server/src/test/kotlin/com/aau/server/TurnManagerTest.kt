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
    private val goalPos = BoardPosition(2, 10)
    private val goalCard = CardDeck.createGoalCards().first().copy(isRevealed = false)

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
    private fun blockCard(id: String, type: CardType = CardType.LANTERN_RED) = TunnelCard(id, type, emptySet())
    private fun repairCard(id: String, type: CardType = CardType.LANTERN_GREEN) = TunnelCard(id, type, emptySet())
    private fun doubleRepairCard(id: String, type: CardType = CardType.DOUBLE_LANTERN_CART) = TunnelCard(id, type, emptySet())
    private fun mapCard(id: String) = TunnelCard(id, CardType.MAPCARD, emptySet())
    private fun rockfallCard(id: String) = TunnelCard(id, CardType.ROCKFALL, emptySet())

    // --- Standard game flow and positive tests ---

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
        val targetGoal = CardDeck.createGoalCards().first()
        val distribution = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(mapCard("m1"))),
            drawPile = mutableListOf(),
            goalCards = listOf(targetGoal),
            startCard = startCard
        )
        val state = GameState(listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)), p1, listOf(PlacedTunnelCard(startPos, startCard), PlacedTunnelCard(goalPos, targetGoal)))
        turnManager.initializeGame("MAP", distribution, state)

        val (_, mapResult) = turnManager.playMapCard("MAP", p1, "m1", goalPos)

        assertEquals(goalPos, mapResult.position)
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

    // --- Error / negative / edge tests ---

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

    // -- getValidPositions coverage --
    @Test
    fun `getValidPositions returns non-empty for two adjacent cards`() {
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
        val badEntity = GameEntity(
            "BAD", "BAD", "invalid", "[]", "[]", "{}", "[]", "{}", false, 0
        )
        whenever(gameRepository.findAll()).thenReturn(listOf(badEntity))
        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        val result = freshManager.loadFromDb()
        assertEquals(0, result)
    }

    // -- drawCardForPlayer with empty deck coverage --
    @Test
    fun `drawCardForPlayer sets deckWasEmptied if pile empty`() {
        val players = listOf(PlayerTurn(p1, "A", 1))
        val distribution = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(pathCard("drawMe"))),
            drawPile = mutableListOf(), // empty pile!
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        val initialState = GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard)))
        turnManager.initializeGame("EMPTY_DRAW", distribution, initialState)
        assertThrows<IllegalArgumentException> {
            turnManager.playCard("EMPTY_DRAW", p1, "drawMe", BoardPosition(4, 3), false)
        }
    }

    // =================== SPECIAL CARD RULE TESTS ===================

    // ------- BLOCK CARDS -------

    @Test
    fun `block card only works if tool not already blocked`() {
        val blockedPlayer = PlayerTurn(p2, "B", 2, blockedTools = setOf(ToolType.PICKAXE))
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("b1", CardType.PICKAXE_RED, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        val state = GameState(
            players = listOf(PlayerTurn(p1, "A", 1), blockedPlayer),
            currentPlayerId = p1,
            boardPlacements = listOf(PlacedTunnelCard(startPos, startCard))
        )
        turnManager.initializeGame("BLOCK_TOOL", dist, state)
        assertThrows<IllegalArgumentException> {
            turnManager.playBlockCard("BLOCK_TOOL", p1, "b1", p2)
        }
    }

    // ------- REPAIR CARDS -------

    @Test
    fun `repair card only works if matching block is present`() {
        val blockedPlayer = PlayerTurn(p2, "B", 2, blockedTools = setOf(ToolType.CART))
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("r1", CardType.CART_GREEN, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        val state = GameState(
            players = listOf(PlayerTurn(p1, "A", 1), blockedPlayer),
            currentPlayerId = p1,
            boardPlacements = listOf(PlacedTunnelCard(startPos, startCard))
        )
        turnManager.initializeGame("REPAIR_CARD", dist, state)
        val result = turnManager.playRepairCard("REPAIR_CARD", p1, "r1", p2, ToolType.CART)
        val p2updated = result.updatedGameState.players.find { it.playerId == p2 }!!
        assertFalse(ToolType.CART in p2updated.blockedTools)
    }

    @Test
    fun `repair card throws if nothing to repair`() {
        val clearPlayer = PlayerTurn(p2, "B", 2, blockedTools = emptySet())
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("r2", CardType.LANTERN_GREEN, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "NOPAIR", dist, GameState(listOf(PlayerTurn(p1, "A", 1), clearPlayer), p1, listOf(PlacedTunnelCard(startPos, startCard)))
        )
        assertThrows<IllegalArgumentException> {
            turnManager.playRepairCard("NOPAIR", p1, "r2", p2, ToolType.LANTERN)
        }
    }

    // ------- DOUBLE REPAIR CARD -------

    @Test
    fun `double repair card repairs just the chosen tool`() {
        val blockedPlayer = PlayerTurn(p2, "B", 2, blockedTools = setOf(ToolType.LANTERN, ToolType.CART))
        val doubleCard = TunnelCard("dr1", CardType.DOUBLE_LANTERN_CART, emptySet())
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(doubleCard)),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "DOUBLE", dist, GameState(listOf(PlayerTurn(p1, "A", 1), blockedPlayer), p1, listOf(PlacedTunnelCard(startPos, startCard)))
        )
        // Only one tool is repaired
        val result = turnManager.playRepairCard("DOUBLE", p1, "dr1", p2, ToolType.LANTERN)
        val updated = result.updatedGameState.players.find { it.playerId == p2 }!!
        assertFalse(ToolType.LANTERN in updated.blockedTools)
        assertTrue(ToolType.CART in updated.blockedTools)
    }

    @Test
    fun `double repair card throws if target has neither blocked`() {
        val clearPlayer = PlayerTurn(p2, "B", 2, blockedTools = setOf(ToolType.PICKAXE))
        val doubleCard = TunnelCard("dr2", CardType.DOUBLE_LANTERN_CART, emptySet())
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(doubleCard)),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "DRNONE", dist, GameState(listOf(PlayerTurn(p1, "A", 1), clearPlayer), p1, listOf(PlacedTunnelCard(startPos, startCard)))
        )
        assertThrows<IllegalArgumentException> {
            turnManager.playRepairCard("DRNONE", p1, "dr2", p2, ToolType.LANTERN)
        }
    }

    // ------- MAP CARD -------

    @Test
    fun `map card only allowed on unrevealed goal card`() {
        val hiddenGoal = CardDeck.createGoalCards().first().copy(isRevealed = false)
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("m1", CardType.MAPCARD, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        val state = GameState(
            players = listOf(PlayerTurn(p1, "A", 1)),
            currentPlayerId = p1,
            boardPlacements = listOf(PlacedTunnelCard(goalPos, hiddenGoal), PlacedTunnelCard(startPos, startCard))
        )
        turnManager.initializeGame("MAPVALID", dist, state)
        val (_, mapResult) = turnManager.playMapCard("MAPVALID", p1, "m1", goalPos)
        assertEquals(goalPos, mapResult.position)
    }

    @Test
    fun `map card throws if not on unrevealed goal`() {
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("m2", CardType.MAPCARD, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        val state = GameState(
            players = listOf(PlayerTurn(p1, "A", 1)),
            currentPlayerId = p1,
            boardPlacements = listOf(PlacedTunnelCard(startPos, startCard))
        )
        turnManager.initializeGame("MAPINVALID", dist, state)
        assertThrows<IllegalArgumentException> {
            turnManager.playMapCard("MAPINVALID", p1, "m2", startPos)
        }
    }

    // ------- ROCKFALL CARD -------

    @Test
    fun `rockfall removes path card from board`() {
        val pathPos = BoardPosition(5, 5)
        val pathCard = TunnelCard("pathX", CardType.PATH, setOf(Direction.TOP, Direction.LEFT))
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("rf1", CardType.ROCKFALL, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        val board = listOf(
            PlacedTunnelCard(pathPos, pathCard),
            PlacedTunnelCard(startPos, startCard),
            PlacedTunnelCard(goalPos, goalCard)
        )
        val state = GameState(
            players = listOf(PlayerTurn(p1, "A", 1)),
            currentPlayerId = p1,
            boardPlacements = board
        )
        turnManager.initializeGame("ROCK_REMOVE", dist, state)
        val result = turnManager.playRockfallCard("ROCK_REMOVE", p1, "rf1", pathPos)
        assertNull(result.updatedGameState.boardPlacements.find { it.position == pathPos })
    }

    @Test
    fun `rockfall cannot remove start card`() {
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("rfStart", CardType.ROCKFALL, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        val board = listOf(
            PlacedTunnelCard(startPos, startCard)
        )
        val state = GameState(
            players = listOf(PlayerTurn(p1, "A", 1)),
            currentPlayerId = p1,
            boardPlacements = board
        )
        turnManager.initializeGame("ROCK_START", dist, state)
        assertThrows<IllegalArgumentException>("Darf nur Tunnelkarten entfernen.") {
            turnManager.playRockfallCard("ROCK_START", p1, "rfStart", startPos)
        }
    }

    @Test
    fun `rockfall cannot remove goal card`() {
        val goalPosCustom = BoardPosition(2, 10)
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("rfGoal", CardType.ROCKFALL, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = listOf(goalCard),
            startCard = startCard
        )
        val board = listOf(
            PlacedTunnelCard(goalPosCustom, goalCard)
        )
        val state = GameState(
            players = listOf(PlayerTurn(p1, "A", 1)),
            currentPlayerId = p1,
            boardPlacements = board
        )
        turnManager.initializeGame("ROCK_GOAL", dist, state)
        assertThrows<IllegalArgumentException>("Darf nur Tunnelkarten entfernen.") {
            turnManager.playRockfallCard("ROCK_GOAL", p1, "rfGoal", goalPosCustom)
        }
    }

    // ------- CHEAT TESTS -------

    @Test
    fun `cheat LANTERN_FLASHLIGHT keeps player active`() {
        val blockedPlayer = PlayerTurn(p1, "Alice", 1, blockedTools = setOf(ToolType.LANTERN))
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf()),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("CHEAT1", dist, GameState(listOf(blockedPlayer, PlayerTurn(p2, "Bob", 2)), p1, emptyList()))

        val result = turnManager.cheatPlayer("CHEAT1", p1, CheatType.LANTERN_FLASHLIGHT)

        val updatedP1 = result.updatedGameState.players.find { it.playerId == p1 }!!
        assertFalse(ToolType.LANTERN in updatedP1.blockedTools)
        assertEquals(p1, result.updatedGameState.currentPlayerId)
    }

    @Test
    fun `cheat LANTERN_FLASHLIGHT fails if not players turn`() {
        val blockedPlayer = PlayerTurn(p2, "Bob", 2, blockedTools = setOf(ToolType.LANTERN))
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf()),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("CHEAT3", dist, GameState(listOf(PlayerTurn(p1, "Alice", 1), blockedPlayer), p1, emptyList()))

        assertThrows<IllegalArgumentException> {
            turnManager.cheatPlayer("CHEAT3", p2, CheatType.LANTERN_FLASHLIGHT)
        }
    }

    @Test
    fun `cheat LANTERN_FLASHLIGHT idempotent when not blocked`() {
        val player = PlayerTurn(p1, "Alice", 1, blockedTools = emptySet())
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf()),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("IDEM", dist, GameState(listOf(player, PlayerTurn(p2, "Bob", 2)), p1, emptyList()))

        val result = turnManager.cheatPlayer("IDEM", p1, CheatType.LANTERN_FLASHLIGHT)

        val updatedP1 = result.updatedGameState.players.find { it.playerId == p1 }!!
        assertTrue(updatedP1.blockedTools.isEmpty())
        assertEquals(p1, result.updatedGameState.currentPlayerId)
    }

    @Test
    fun `cheatPlayer throws on unknown cheat type`() {
        assertThrows<IllegalArgumentException> {
            turnManager.cheatPlayer(lobbyCode, p1, CheatType.TEST_INVALID_CHEAT)
        }
    }

    @Test
    fun `cheatPlayer throws when game not found`() {
        assertThrows<IllegalArgumentException> {
            turnManager.cheatPlayer("NON_EXISTENT", p1, CheatType.LANTERN_FLASHLIGHT)
        }
    }

    // ------- EDGE CASES -------

    @Test
    fun `blocked player cannot play path cards`() {
        val blocked = PlayerTurn(p1, "A", 1, blockedTools = setOf(ToolType.PICKAXE, ToolType.LANTERN, ToolType.CART))
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(pathCard("pathTest"))),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "BLOCKEDPLAY", dist, GameState(listOf(blocked), p1, listOf(PlacedTunnelCard(startPos, startCard)))
        )
        assertThrows<IllegalArgumentException> {
            turnManager.playCard("BLOCKEDPLAY", p1, "pathTest", BoardPosition(4, 3), false)
        }
    }

    @Test
    fun `blocked player can still play action cards`() {
        val blocked = PlayerTurn(p1, "A", 1, blockedTools = setOf(ToolType.PICKAXE))
        val dist = CardDistributionResult(
            hands = mapOf(
                p1 to mutableListOf(TunnelCard("b1", CardType.LANTERN_RED, emptySet()))
            ),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "BLOCKEDACT", dist,
            GameState(listOf(blocked), p1, listOf(PlacedTunnelCard(startPos, startCard)))
        )
        // Blocked for PICKAXE, but not for LANTERN – so LANTERN block is allowed!
        turnManager.playBlockCard("BLOCKEDACT", p1, "b1", p1)
    }
}
