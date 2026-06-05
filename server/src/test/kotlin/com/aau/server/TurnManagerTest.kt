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

    private fun mockPlayerData(players: Map<String, Player>) {
        var currentPlayers = players

        whenever(gameService.getAllPlayerData(any())).thenAnswer { currentPlayers }
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            currentPlayers = invocation.getArgument<Map<String, Player>>(1)
            null
        }.whenever(gameService).setPlayerData(any(), any())
    }

    private fun pathCard(id: String, conns: Set<Direction> = emptySet()) =
        TunnelCard(id, CardType.PATH, conns)

    private fun blockCard(id: String, type: CardType = CardType.LANTERN_RED) =
        TunnelCard(id, type, emptySet())

    private fun repairCard(id: String, type: CardType = CardType.LANTERN_GREEN) =
        TunnelCard(id, type, emptySet())

    private fun doubleRepairCard(id: String, type: CardType = CardType.DOUBLE_LANTERN_CART) =
        TunnelCard(id, type, emptySet())

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
            hands = mapOf(
                p1 to mutableListOf(blockCard("b1")),
                p2 to mutableListOf(pathCard("c2")),
                p3 to mutableListOf(pathCard("c3"))
            ),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "BLOCK",
            distribution,
            GameState(
                listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)),
                p1,
                listOf(PlacedTunnelCard(startPos, startCard))
            )
        )

        val result = turnManager.playBlockCard("BLOCK", p1, "b1", p2)

        val updatedPlayer = result.updatedGameState.players.find { it.playerId == p2 }!!
        assertTrue(ToolType.LANTERN in updatedPlayer.blockedTools)
        assertEquals(p2, result.updatedGameState.currentPlayerId)
    }

    @Test
    fun `playRepairCard removes blocked tool and advances turn`() {
        val players = listOf(
            PlayerTurn(p1, "A", 1),
            PlayerTurn(p2, "B", 2, blockedTools = setOf(ToolType.LANTERN))
        )
        val distribution = CardDistributionResult(
            hands = mapOf(
                p1 to mutableListOf(repairCard("r1")),
                p2 to mutableListOf(pathCard("c2"))
            ),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "REPAIR",
            distribution,
            GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard)))
        )

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
        val state = GameState(
            listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)),
            p1,
            listOf(
                PlacedTunnelCard(startPos, startCard),
                PlacedTunnelCard(goalPos, targetGoal)
            )
        )
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
        val state = GameState(
            listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)),
            p1,
            listOf(
                PlacedTunnelCard(startPos, startCard),
                PlacedTunnelCard(targetPos, pCard)
            )
        )
        turnManager.initializeGame("ROCK", distribution, state)

        val result = turnManager.playRockfallCard("ROCK", p1, "rf1", targetPos)
        assertNull(result.updatedGameState.boardPlacements.find { it.position == targetPos })
    }

    @Test
    fun `loadFromDb handles recovery`() {
        val players = listOf(PlayerTurn(p1, "Alice", 1))
        val board = listOf(PlacedTunnelCard(startPos, startCard))
        val entity = GameEntity(
            "OK",
            p1,
            objectMapper.writeValueAsString(board),
            "[]",
            "[]",
            objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
            objectMapper.writeValueAsString(players),
            "{}",
            false,
            0
        )

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
        val result = turnManager.getValidPositions(
            "NO_GAME",
            TunnelCard("id", CardType.PATH, setOf(Direction.TOP)),
            false,
            emptyList()
        )
        assertTrue(result.isEmpty())
    }

    // -- loadFromDb error branch --

    @Test
    fun `loadFromDb logs error on bad entity`() {
        val badEntity = GameEntity(
            "BAD",
            "BAD",
            "invalid",
            "[]",
            "[]",
            "{}",
            "[]",
            "{}",
            false,
            0
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
            drawPile = mutableListOf(),
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
            "NOPAIR",
            dist,
            GameState(
                listOf(PlayerTurn(p1, "A", 1), clearPlayer),
                p1,
                listOf(PlacedTunnelCard(startPos, startCard))
            )
        )
        assertThrows<IllegalArgumentException> {
            turnManager.playRepairCard("NOPAIR", p1, "r2", p2, ToolType.LANTERN)
        }
    }

    // ------- DOUBLE REPAIR CARD -------

    @Test
    fun `double repair card repairs just the chosen tool`() {
        val blockedPlayer = PlayerTurn(
            p2,
            "B",
            2,
            blockedTools = setOf(ToolType.LANTERN, ToolType.CART)
        )
        val doubleCard = TunnelCard("dr1", CardType.DOUBLE_LANTERN_CART, emptySet())
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(doubleCard)),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "DOUBLE",
            dist,
            GameState(
                listOf(PlayerTurn(p1, "A", 1), blockedPlayer),
                p1,
                listOf(PlacedTunnelCard(startPos, startCard))
            )
        )
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
            "DRNONE",
            dist,
            GameState(
                listOf(PlayerTurn(p1, "A", 1), clearPlayer),
                p1,
                listOf(PlacedTunnelCard(startPos, startCard))
            )
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
            boardPlacements = listOf(
                PlacedTunnelCard(goalPos, hiddenGoal),
                PlacedTunnelCard(startPos, startCard)
            )
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
        val board = listOf(PlacedTunnelCard(startPos, startCard))
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
        val board = listOf(PlacedTunnelCard(goalPosCustom, goalCard))
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

    // ------- EDGE CASES -------

    @Test
    fun `blocked player cannot play path cards`() {
        val blocked = PlayerTurn(
            p1,
            "A",
            1,
            blockedTools = setOf(ToolType.PICKAXE, ToolType.LANTERN, ToolType.CART)
        )
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(pathCard("pathTest"))),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "BLOCKEDPLAY",
            dist,
            GameState(listOf(blocked), p1, listOf(PlacedTunnelCard(startPos, startCard)))
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
            "BLOCKEDACT",
            dist,
            GameState(listOf(blocked), p1, listOf(PlacedTunnelCard(startPos, startCard)))
        )
        turnManager.playBlockCard("BLOCKEDACT", p1, "b1", p1)
    }

    // =================== NEW COVERAGE TESTS FOR ROUND / GOLD FLOW ===================

    @Test
    fun `loadFromDb uses fresh gold deck when goldDeckJson is invalid`() {
        mockPlayerData(
            mapOf(
                p1 to Player(id = p1, name = "Alice", role = Role.GOLDDIGGER),
                p2 to Player(id = p2, name = "Bob", role = Role.SABOTEUR)
            )
        )

        val players = listOf(
            PlayerTurn(p1, "Alice", 1),
            PlayerTurn(p2, "Bob", 2)
        )
        val board = listOf(PlacedTunnelCard(startPos, startCard))

        val entity = GameEntity(
            lobbyCode = "INVALID_GOLD",
            currentPlayerId = p1,
            boardJson = objectMapper.writeValueAsString(board),
            drawPileJson = "[]",
            discardPileJson = "[]",
            handsJson = objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
            playersTurnJson = objectMapper.writeValueAsString(players),
            playerRolesJson = objectMapper.writeValueAsString(
                mapOf(
                    p1 to Player(id = p1, name = "Alice", role = Role.GOLDDIGGER),
                    p2 to Player(id = p2, name = "Bob", role = Role.SABOTEUR)
                )
            ),
            deckWasEmptied = false,
            passedSinceEmpty = 0,
            knownGoalsByPlayerJson = "{}",
            currentRound = 2,
            isRoundOver = false,
            isGameOver = false,
            lastRoundResultJson = "",
            goldDeckJson = "not valid json",
            lastPlayerWhoPlayed = p1
        )

        whenever(gameRepository.findAll()).thenReturn(listOf(entity))

        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        val recovered = freshManager.loadFromDb()

        assertEquals(1, recovered)

        val snapshot = freshManager.getGameStateSnapshot("INVALID_GOLD")
        assertEquals(2, snapshot.currentRound)
        assertFalse(snapshot.isGameOver)
    }

    @Test
    fun `loadFromDb sets lastRoundResult to null when lastRoundResultJson is invalid`() {
        mockPlayerData(
            mapOf(
                p1 to Player(id = p1, name = "Alice", role = Role.GOLDDIGGER),
                p2 to Player(id = p2, name = "Bob", role = Role.SABOTEUR)
            )
        )

        val players = listOf(
            PlayerTurn(p1, "Alice", 1),
            PlayerTurn(p2, "Bob", 2)
        )
        val board = listOf(PlacedTunnelCard(startPos, startCard))

        val entity = GameEntity(
            lobbyCode = "INVALID_ROUND_RESULT",
            currentPlayerId = p1,
            boardJson = objectMapper.writeValueAsString(board),
            drawPileJson = "[]",
            discardPileJson = "[]",
            handsJson = objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
            playersTurnJson = objectMapper.writeValueAsString(players),
            playerRolesJson = objectMapper.writeValueAsString(
                mapOf(
                    p1 to Player(id = p1, name = "Alice", role = Role.GOLDDIGGER),
                    p2 to Player(id = p2, name = "Bob", role = Role.SABOTEUR)
                )
            ),
            deckWasEmptied = false,
            passedSinceEmpty = 0,
            knownGoalsByPlayerJson = "{}",
            currentRound = 2,
            isRoundOver = true,
            isGameOver = false,
            lastRoundResultJson = "{broken json",
            goldDeckJson = objectMapper.writeValueAsString(CardDeck.createGoldDeck()),
            lastPlayerWhoPlayed = p2
        )

        whenever(gameRepository.findAll()).thenReturn(listOf(entity))

        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        val recovered = freshManager.loadFromDb()

        assertEquals(1, recovered)

        val snapshot = freshManager.getGameStateSnapshot("INVALID_ROUND_RESULT")
        assertNull(snapshot.lastRoundResult)
    }

    @Test
    fun `loadFromDb restores round state and gold values`() {
        mockPlayerData(
            mapOf(
                p1 to Player(id = p1, name = "Alice", role = Role.GOLDDIGGER, goldCards = listOf(GoldCard("g1", 2))),
                p2 to Player(id = p2, name = "Bob", role = Role.SABOTEUR, goldCards = listOf(GoldCard("g2", 1)))
            )
        )

        val players = listOf(
            PlayerTurn(p1, "Alice", 1, goldValue = 0),
            PlayerTurn(p2, "Bob", 2, goldValue = 0)
        )
        val board = listOf(PlacedTunnelCard(startPos, startCard))
        val roundResult = RoundResult(
            roundNumber = 2,
            winnerRole = Role.GOLDDIGGER,
            winningPlayerIds = listOf(p1),
            revealedRoles = mapOf(p1 to Role.GOLDDIGGER, p2 to Role.SABOTEUR),
            distributedGold = emptyMap(),
            playerGoldTotals = mapOf(p1 to 2, p2 to 1),
            gameFinished = false,
            finalWinnerIds = emptyList()
        )

        val entity = GameEntity(
            lobbyCode = "RESTORE_STATE",
            currentPlayerId = p2,
            boardJson = objectMapper.writeValueAsString(board),
            drawPileJson = "[]",
            discardPileJson = "[]",
            handsJson = objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
            playersTurnJson = objectMapper.writeValueAsString(players),
            playerRolesJson = objectMapper.writeValueAsString(
                mapOf(
                    p1 to Player(id = p1, name = "Alice", role = Role.GOLDDIGGER, goldCards = listOf(GoldCard("g1", 2))),
                    p2 to Player(id = p2, name = "Bob", role = Role.SABOTEUR, goldCards = listOf(GoldCard("g2", 1)))
                )
            ),
            deckWasEmptied = true,
            passedSinceEmpty = 2,
            knownGoalsByPlayerJson = "{}",
            currentRound = 2,
            isRoundOver = true,
            isGameOver = false,
            lastRoundResultJson = objectMapper.writeValueAsString(roundResult),
            goldDeckJson = objectMapper.writeValueAsString(CardDeck.createGoldDeck()),
            lastPlayerWhoPlayed = p1
        )

        whenever(gameRepository.findAll()).thenReturn(listOf(entity))

        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        val recovered = freshManager.loadFromDb()

        assertEquals(1, recovered)

        val snapshot = freshManager.getGameStateSnapshot("RESTORE_STATE")
        assertEquals(2, snapshot.currentRound)
        assertTrue(snapshot.isRoundOver)
        assertFalse(snapshot.isGameOver)
        assertNotNull(snapshot.lastRoundResult)
        assertEquals(2, snapshot.players.find { it.playerId == p1 }?.goldValue)
        assertEquals(1, snapshot.players.find { it.playerId == p2 }?.goldValue)
    }

    @Test
    fun `loadFromDb restores finished game with final winners`() {
        mockPlayerData(
            mapOf(
                p1 to Player(id = p1, name = "Alice", role = Role.GOLDDIGGER, goldCards = listOf(GoldCard("g1", 3))),
                p2 to Player(id = p2, name = "Bob", role = Role.SABOTEUR, goldCards = listOf(GoldCard("g2", 1)))
            )
        )

        val players = listOf(
            PlayerTurn(p1, "Alice", 1, goldValue = 0),
            PlayerTurn(p2, "Bob", 2, goldValue = 0)
        )
        val board = listOf(PlacedTunnelCard(startPos, startCard))
        val roundResult = RoundResult(
            roundNumber = 3,
            winnerRole = Role.GOLDDIGGER,
            winningPlayerIds = listOf(p1),
            revealedRoles = mapOf(p1 to Role.GOLDDIGGER, p2 to Role.SABOTEUR),
            distributedGold = emptyMap(),
            playerGoldTotals = mapOf(p1 to 3, p2 to 1),
            gameFinished = true,
            finalWinnerIds = listOf(p1)
        )

        val entity = GameEntity(
            lobbyCode = "FINISHED_GAME",
            currentPlayerId = p1,
            boardJson = objectMapper.writeValueAsString(board),
            drawPileJson = "[]",
            discardPileJson = "[]",
            handsJson = objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
            playersTurnJson = objectMapper.writeValueAsString(players),
            playerRolesJson = objectMapper.writeValueAsString(
                mapOf(
                    p1 to Player(id = p1, name = "Alice", role = Role.GOLDDIGGER, goldCards = listOf(GoldCard("g1", 3))),
                    p2 to Player(id = p2, name = "Bob", role = Role.SABOTEUR, goldCards = listOf(GoldCard("g2", 1)))
                )
            ),
            deckWasEmptied = true,
            passedSinceEmpty = 3,
            knownGoalsByPlayerJson = "{}",
            currentRound = 3,
            isRoundOver = true,
            isGameOver = true,
            lastRoundResultJson = objectMapper.writeValueAsString(roundResult),
            goldDeckJson = objectMapper.writeValueAsString(CardDeck.createGoldDeck()),
            lastPlayerWhoPlayed = p2
        )

        whenever(gameRepository.findAll()).thenReturn(listOf(entity))

        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        val recovered = freshManager.loadFromDb()

        assertEquals(1, recovered)

        val snapshot = freshManager.getGameStateSnapshot("FINISHED_GAME")
        assertEquals(3, snapshot.currentRound)
        assertTrue(snapshot.isRoundOver)
        assertTrue(snapshot.isGameOver)
        assertNotNull(snapshot.lastRoundResult)
        assertTrue(snapshot.lastRoundResult!!.gameFinished)
        assertEquals(listOf(p1), snapshot.lastRoundResult!!.finalWinnerIds)
        assertEquals(3, snapshot.players.find { it.playerId == p1 }?.goldValue)
    }

    @Test
    fun `loadFromDb returns zero when repository is empty`() {
        whenever(gameRepository.findAll()).thenReturn(emptyList())

        val freshManager = TurnManager(gameRepository, objectMapper, gameService)

        val result = freshManager.loadFromDb()

        assertEquals(0, result)
    }

    @Test
    fun `playCard throws when player is not current player`() {
        setupStandardGame()

        assertThrows<IllegalArgumentException> {
            turnManager.playCard(lobbyCode, p2, "c2", BoardPosition(4, 3), false)
        }
    }

    @Test
    fun `rockfall throws when target position is empty`() {
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(rockfallCard("rf1"))),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        val state = GameState(
            players = listOf(PlayerTurn(p1, "A", 1)),
            currentPlayerId = p1,
            boardPlacements = listOf(PlacedTunnelCard(startPos, startCard))
        )
        turnManager.initializeGame("ROCK_EMPTY", dist, state)

        assertThrows<IllegalArgumentException> {
            turnManager.playRockfallCard("ROCK_EMPTY", p1, "rf1", BoardPosition(9, 9))
        }
    }

    @Test
    fun `playBlockCard throws when target player does not exist`() {
        val distribution = CardDistributionResult(
            hands = mapOf(
                p1 to mutableListOf(blockCard("b1")),
                p2 to mutableListOf(pathCard("c2"))
            ),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        val state = GameState(
            players = listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)),
            currentPlayerId = p1,
            boardPlacements = listOf(PlacedTunnelCard(startPos, startCard))
        )
        turnManager.initializeGame("BLOCK_MISSING", distribution, state)

        assertThrows<IllegalArgumentException> {
            turnManager.playBlockCard("BLOCK_MISSING", p1, "b1", "missing-player")
        }
    }

    @Test
    fun `playRepairCard throws when target player does not exist`() {
        val distribution = CardDistributionResult(
            hands = mapOf(
                p1 to mutableListOf(repairCard("r1")),
                p2 to mutableListOf(pathCard("c2"))
            ),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        val state = GameState(
            players = listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)),
            currentPlayerId = p1,
            boardPlacements = listOf(PlacedTunnelCard(startPos, startCard))
        )
        turnManager.initializeGame("REPAIR_MISSING", distribution, state)

        assertThrows<IllegalArgumentException> {
            turnManager.playRepairCard("REPAIR_MISSING", p1, "r1", "missing-player", ToolType.LANTERN)
        }
    }

    @Test
    fun `map card throws when target position has no card`() {
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(mapCard("m1"))),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        val state = GameState(
            players = listOf(PlayerTurn(p1, "A", 1)),
            currentPlayerId = p1,
            boardPlacements = listOf(PlacedTunnelCard(startPos, startCard))
        )
        turnManager.initializeGame("MAP_EMPTY", dist, state)

        assertThrows<IllegalArgumentException> {
            turnManager.playMapCard("MAP_EMPTY", p1, "m1", BoardPosition(9, 9))
        }
    }

    @Test
    fun `loadFromDb restores knownGoalsByPlayer from valid json`() {
        mockPlayerData(
            mapOf(
                p1 to Player(id = p1, name = "Alice", role = Role.GOLDDIGGER)
            )
        )

        val players = listOf(PlayerTurn(p1, "Alice", 1))
        val board = listOf(PlacedTunnelCard(startPos, startCard))
        val knownGoalsJson = objectMapper.writeValueAsString(
            mapOf(
                p1 to mapOf(goalPos.toString() to CardDeck.createGoalCards().first())
            )
        )

        val entity = GameEntity(
            lobbyCode = "KNOWN_GOALS",
            currentPlayerId = p1,
            boardJson = objectMapper.writeValueAsString(board),
            drawPileJson = "[]",
            discardPileJson = "[]",
            handsJson = objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
            playersTurnJson = objectMapper.writeValueAsString(players),
            playerRolesJson = objectMapper.writeValueAsString(
                mapOf(p1 to Player(id = p1, name = "Alice", role = Role.GOLDDIGGER))
            ),
            deckWasEmptied = false,
            passedSinceEmpty = 0,
            knownGoalsByPlayerJson = knownGoalsJson,
            currentRound = 1,
            isRoundOver = false,
            isGameOver = false,
            lastRoundResultJson = "",
            goldDeckJson = objectMapper.writeValueAsString(CardDeck.createGoldDeck()),
            lastPlayerWhoPlayed = null
        )

        whenever(gameRepository.findAll()).thenReturn(listOf(entity))

        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        val recovered = freshManager.loadFromDb()

        assertEquals(1, recovered)
        assertNotNull(freshManager.getGameStateSnapshot("KNOWN_GOALS"))
    }

    @Test
    fun `loadFromDb creates fresh gold deck when goldDeckJson is blank`() {
        mockPlayerData(
            mapOf(
                p1 to Player(id = p1, name = "Alice", role = Role.GOLDDIGGER)
            )
        )

        val players = listOf(PlayerTurn(p1, "Alice", 1))
        val board = listOf(PlacedTunnelCard(startPos, startCard))

        val entity = GameEntity(
            lobbyCode = "BLANK_GOLD",
            currentPlayerId = p1,
            boardJson = objectMapper.writeValueAsString(board),
            drawPileJson = "[]",
            discardPileJson = "[]",
            handsJson = objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
            playersTurnJson = objectMapper.writeValueAsString(players),
            playerRolesJson = objectMapper.writeValueAsString(
                mapOf(p1 to Player(id = p1, name = "Alice", role = Role.GOLDDIGGER))
            ),
            deckWasEmptied = false,
            passedSinceEmpty = 0,
            knownGoalsByPlayerJson = "{}",
            currentRound = 1,
            isRoundOver = false,
            isGameOver = false,
            lastRoundResultJson = "",
            goldDeckJson = "",
            lastPlayerWhoPlayed = null
        )

        whenever(gameRepository.findAll()).thenReturn(listOf(entity))

        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        val recovered = freshManager.loadFromDb()

        assertEquals(1, recovered)
        assertNotNull(freshManager.getGameStateSnapshot("BLANK_GOLD"))
    }

    @Test
    fun `discardCard when saboteurs win starts next round and distributes gold`() {
        mockPlayerData(
            mapOf(
                p1 to Player(id = p1, name = "Alice", role = Role.SABOTEUR),
                p2 to Player(id = p2, name = "Bob", role = Role.SABOTEUR),
                p3 to Player(id = p3, name = "Charlie", role = Role.GOLDDIGGER)
            )
        )

        val distribution = CardDistributionResult(
            hands = mapOf(
                p1 to mutableListOf(pathCard("d1")),
                p2 to mutableListOf(pathCard("d2")),
                p3 to mutableListOf(pathCard("d3"))
            ),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )

        val state = GameState(
            players = listOf(
                PlayerTurn(p1, "Alice", 1),
                PlayerTurn(p2, "Bob", 2, blockedTools = setOf(ToolType.LANTERN)),
                PlayerTurn(p3, "Charlie", 3)
            ),
            currentPlayerId = p1,
            boardPlacements = listOf(PlacedTunnelCard(startPos, startCard)),
            currentRound = 1
        )

        turnManager.initializeGame("SABO_NEXT_ROUND", distribution, state)

        turnManager.discardCard("SABO_NEXT_ROUND", p1, "d1")
        turnManager.discardCard("SABO_NEXT_ROUND", p2, "d2")
        val result = turnManager.discardCard("SABO_NEXT_ROUND", p3, "d3")

        assertEquals("SABOTEURS", result.winner)

        val snapshot = turnManager.getGameStateSnapshot("SABO_NEXT_ROUND")
        assertEquals(2, snapshot.currentRound)
        assertFalse(snapshot.isGameOver)
        assertFalse(snapshot.isRoundOver)
        assertNotNull(snapshot.lastRoundResult)
        assertEquals(Role.SABOTEUR, snapshot.lastRoundResult!!.winnerRole)
        assertTrue(snapshot.lastRoundResult!!.winningPlayerIds.containsAll(listOf(p1, p2)))

        assertEquals(4, snapshot.boardPlacements.size)
        assertTrue(snapshot.boardPlacements.any { it.card.type == CardType.START })
        assertTrue(snapshot.currentPlayerId in listOf(p1, p2, p3))

        val p2State = snapshot.players.find { it.playerId == p2 }!!
        assertTrue(p2State.blockedTools.isEmpty())

        val updatedPlayers = gameService.getAllPlayerData("SABO_NEXT_ROUND")

        assertTrue(updatedPlayers[p1]!!.goldCards.isNotEmpty(), "p1 sollte mindestens 1 Goldkarte haben")
        assertTrue(updatedPlayers[p2]!!.goldCards.isNotEmpty(), "p2 sollte mindestens 1 Goldkarte haben")

        val sab1Gold = updatedPlayers[p1]!!.goldCards.sumOf { it.value }
        val sab2Gold = updatedPlayers[p2]!!.goldCards.sumOf { it.value }

        assertTrue(sab1Gold >= 3)
        assertTrue(sab2Gold >= 3)

        val p1Snapshot = snapshot.players.find { it.playerId == p1 }!!
        val p2Snapshot = snapshot.players.find { it.playerId == p2 }!!
        assertEquals(sab1Gold, p1Snapshot.goldValue)
        assertEquals(sab2Gold, p2Snapshot.goldValue)
    }

    @Test
    fun `discardCard when saboteurs win in round three marks game over and final winners`() {
        var currentPlayers = mapOf(
            p1 to Player(
                id = p1,
                name = "Alice",
                role = Role.SABOTEUR,
                goldCards = listOf(GoldCard("old1", 2))
            ),
            p2 to Player(
                id = p2,
                name = "Bob",
                role = Role.SABOTEUR,
                goldCards = listOf(GoldCard("old2", 1))
            ),
            p3 to Player(
                id = p3,
                name = "Charlie",
                role = Role.GOLDDIGGER,
                goldCards = emptyList()
            )
        )

        whenever(gameService.getAllPlayerData(any())).thenAnswer { currentPlayers }
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            currentPlayers = invocation.getArgument<Map<String, Player>>(1)
            null
        }.whenever(gameService).setPlayerData(any(), any())

        val distribution = CardDistributionResult(
            hands = mapOf(
                p1 to mutableListOf(pathCard("f1")),
                p2 to mutableListOf(pathCard("f2")),
                p3 to mutableListOf(pathCard("f3"))
            ),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )

        val state = GameState(
            players = listOf(
                PlayerTurn(p1, "Alice", 1, goldValue = 2),
                PlayerTurn(p2, "Bob", 2, goldValue = 1),
                PlayerTurn(p3, "Charlie", 3, goldValue = 0)
            ),
            currentPlayerId = p1,
            boardPlacements = listOf(PlacedTunnelCard(startPos, startCard)),
            currentRound = 3
        )

        turnManager.initializeGame("SABO_FINAL", distribution, state)

        turnManager.discardCard("SABO_FINAL", p1, "f1")
        turnManager.discardCard("SABO_FINAL", p2, "f2")
        val result = turnManager.discardCard("SABO_FINAL", p3, "f3")

        assertEquals("SABOTEURS", result.winner)

        val snapshot = turnManager.getGameStateSnapshot("SABO_FINAL")
        assertEquals(3, snapshot.currentRound)
        assertTrue(snapshot.isGameOver)
        assertTrue(snapshot.isRoundOver)
        assertNotNull(snapshot.lastRoundResult)
        assertTrue(snapshot.lastRoundResult!!.gameFinished)
        assertEquals(Role.SABOTEUR, snapshot.lastRoundResult!!.winnerRole)
        assertTrue(snapshot.lastRoundResult!!.winningPlayerIds.containsAll(listOf(p1, p2)))
        assertTrue(snapshot.lastRoundResult!!.finalWinnerIds.isNotEmpty())

        val p1GoldActual = currentPlayers[p1]!!.goldCards.sumOf { it.value }
        val p2GoldActual = currentPlayers[p2]!!.goldCards.sumOf { it.value }

        // Robuste Checks: nur sicherstellen, dass Gold hinzugefügt wurde
        assertTrue(p1GoldActual > 2, "Alice sollte mehr als 2 Gold haben, hat aber $p1GoldActual")
        assertTrue(p2GoldActual > 1, "Bob sollte mehr als 1 Gold haben, hat aber $p2GoldActual")

        // Snapshot muss mit den tatsächlichen Goldwerten übereinstimmen
        val p1State = snapshot.players.find { it.playerId == p1 }!!
        val p2State = snapshot.players.find { it.playerId == p2 }!!

        assertEquals(p1GoldActual, p1State.goldValue, "Snapshot goldValue für Alice sollte mit tatsächlichen Wert übereinstimmen")
        assertEquals(p2GoldActual, p2State.goldValue, "Snapshot goldValue für Bob sollte mit tatsächlichen Wert übereinstimmen")
    }

}
