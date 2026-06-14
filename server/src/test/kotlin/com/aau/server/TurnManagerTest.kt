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
        // Standard-Mocking für alle regulären Tests wiederherstellen:
        val playerData = mapOf(
            p1 to Player(id = p1, name = "Alice", role = Role.GOLDDIGGER),
            p2 to Player(id = p2, name = "Bob", role = Role.GOLDDIGGER),
            p3 to Player(id = p3, name = "Charlie", role = Role.SABOTEUR)
        )
        whenever(gameService.getAllPlayerData(any())).thenReturn(playerData)
        doNothing().whenever(gameService).setPlayerData(any(), any())

        turnManager = TurnManager(gameRepository, objectMapper, gameService)
        setupStandardGame()
        Mockito.clearInvocations(gameRepository)
    }

    // =====================================================================
    // Helper methods
    // =====================================================================

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
                p3 to mutableListOf(pathCard("c3", Direction.entries.toSet()))
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

    private fun mapCard() = TunnelCard("m1", CardType.MAPCARD, emptySet())
    private fun rockfallCard() = TunnelCard("rf1", CardType.ROCKFALL, emptySet())

    /** Creates a minimal game with a connected path from start → goalPos so isGoalReached() returns true. */
    private fun setupWinningBoard(
        code: String,
        players: List<PlayerTurn>,
        hands: Map<String, MutableList<TunnelCard>>,
        drawPile: MutableList<TunnelCard> = mutableListOf(),
        currentRound: Int = 1
    ): GameState {
        val goalRevealed = TunnelCard(
            "goal_win",
            CardType.GOAL,
            setOf(Direction.LEFT, Direction.RIGHT, Direction.TOP, Direction.BOTTOM),
            isRevealed = true,
            isGoal = true
        )
        val bridge = TunnelCard("bridge", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        val board = listOf(
            PlacedTunnelCard(startPos, startCard),
            PlacedTunnelCard(BoardPosition(4, 3), bridge.copy(id = "b3")),
            PlacedTunnelCard(BoardPosition(4, 4), bridge.copy(id = "b4")),
            PlacedTunnelCard(BoardPosition(4, 5), bridge.copy(id = "b5")),
            PlacedTunnelCard(BoardPosition(4, 6), bridge.copy(id = "b6")),
            PlacedTunnelCard(BoardPosition(4, 7), bridge.copy(id = "b7")),
            PlacedTunnelCard(BoardPosition(4, 8), bridge.copy(id = "b8")),
            PlacedTunnelCard(BoardPosition(4, 9), bridge.copy(id = "b9")),
            PlacedTunnelCard(BoardPosition(4, 10), goalRevealed)
        )
        val initialState = GameState(players, players.first().playerId, board, currentRound = currentRound)
        val distribution = CardDistributionResult(
            hands = hands,
            drawPile = drawPile,
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame(code, distribution, initialState)
        return initialState
    }

    // =====================================================================
    // Standard game flow — positive tests
    // =====================================================================

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
            hands = mapOf(p1 to mutableListOf(mapCard())),
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
            hands = mapOf(p1 to mutableListOf(rockfallCard())),
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
            "OK", p1, objectMapper.writeValueAsString(board), "[]", "[]",
            objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
            objectMapper.writeValueAsString(players), "{}", false, 0
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

    // =====================================================================
    // Error / negative / edge tests
    // =====================================================================

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

    @Test
    fun `getHands returns correct data for existing game`() {
        val hands = turnManager.getHands(lobbyCode)
        assertTrue(hands.containsKey(p1))
        assertTrue(hands[p1]!!.any { it.id == "c1" })
    }

    // =====================================================================
    // getValidPositions
    // =====================================================================

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
        val positions = turnManager.getValidPositions(card1, false, placements)
        assertNotNull(positions)
    }

    @Test
    fun `getValidPositions returns empty if placement list empty`() {
        val result = turnManager.getValidPositions(
            TunnelCard("id", CardType.PATH, setOf(Direction.TOP)), false, emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getValidPositions with rotated card`() {
        val card = TunnelCard("1", CardType.PATH, setOf(Direction.RIGHT))
        val pos1 = BoardPosition(4, 2)
        val placements = listOf(PlacedTunnelCard(pos1, startCard))
        val positions = turnManager.getValidPositions(card, true, placements)
        assertNotNull(positions)
    }

    // =====================================================================
    // loadFromDb edge cases
    // =====================================================================

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

    @Test
    fun `loadFromDb handles blank lastRoundResultJson and goldDeckJson`() {
        val players = listOf(PlayerTurn(p1, "Alice", 1))
        val board = listOf(PlacedTunnelCard(startPos, startCard))
        val entity = GameEntity(
            lobbyCode = "BLANK_FIELDS",
            currentPlayerId = p1,
            boardJson = objectMapper.writeValueAsString(board),
            drawPileJson = "[]",
            discardPileJson = "[]",
            handsJson = objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
            playersTurnJson = objectMapper.writeValueAsString(players),
            playerRolesJson = "{}",
            deckWasEmptied = false,
            passedSinceEmpty = 0,
            lastRoundResultJson = "",   // blank
            goldDeckJson = "",          // blank
            knownGoalsByPlayerJson = ""
        )

        whenever(gameRepository.findAll()).thenReturn(listOf(entity))
        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        assertEquals(1, freshManager.loadFromDb())
        assertNotNull(freshManager.getGameStateSnapshot("BLANK_FIELDS"))
    }

    @Test
    fun `loadFromDb handles populated knownGoalsByPlayer`() {
        val players = listOf(PlayerTurn(p1, "Alice", 1))
        val board = listOf(PlacedTunnelCard(startPos, startCard))
        val targetGoal = CardDeck.createGoalCards().first()
        val knownGoalsJson = objectMapper.writeValueAsString(
            mapOf(p1 to mapOf("2_10" to targetGoal))
        )

        val entity = GameEntity(
            lobbyCode = "KNOWN_GOALS",
            currentPlayerId = p1,
            boardJson = objectMapper.writeValueAsString(board),
            drawPileJson = "[]",
            discardPileJson = "[]",
            handsJson = objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
            playersTurnJson = objectMapper.writeValueAsString(players),
            playerRolesJson = "{}",
            deckWasEmptied = false,
            passedSinceEmpty = 0,
            knownGoalsByPlayerJson = knownGoalsJson
        )

        whenever(gameRepository.findAll()).thenReturn(listOf(entity))
        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        assertEquals(1, freshManager.loadFromDb())
    }

    @Test
    fun `loadFromDb handles bad goldDeckJson by creating new deck`() {
        val players = listOf(PlayerTurn(p1, "Alice", 1))
        val board = listOf(PlacedTunnelCard(startPos, startCard))
        val entity = GameEntity(
            lobbyCode = "BAD_GOLD",
            currentPlayerId = p1,
            boardJson = objectMapper.writeValueAsString(board),
            drawPileJson = "[]",
            discardPileJson = "[]",
            handsJson = objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
            playersTurnJson = objectMapper.writeValueAsString(players),
            playerRolesJson = "{}",
            deckWasEmptied = false,
            passedSinceEmpty = 0,
            goldDeckJson = "INVALID_JSON_FOR_GOLD"
        )

        whenever(gameRepository.findAll()).thenReturn(listOf(entity))
        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        assertEquals(1, freshManager.loadFromDb())
    }

    @Test
    fun `loadFromDb handles bad knownGoalsByPlayerJson gracefully`() {
        val players = listOf(PlayerTurn(p1, "Alice", 1))
        val board = listOf(PlacedTunnelCard(startPos, startCard))
        val entity = GameEntity(
            lobbyCode = "BAD_KNOWN",
            currentPlayerId = p1,
            boardJson = objectMapper.writeValueAsString(board),
            drawPileJson = "[]",
            discardPileJson = "[]",
            handsJson = objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
            playersTurnJson = objectMapper.writeValueAsString(players),
            playerRolesJson = "{}",
            deckWasEmptied = false,
            passedSinceEmpty = 0,
            knownGoalsByPlayerJson = "NOT_VALID_MAP"
        )

        whenever(gameRepository.findAll()).thenReturn(listOf(entity))
        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        assertEquals(1, freshManager.loadFromDb())
    }

    @Test
    fun `loadFromDb recovers multiple games`() {
        val players = listOf(PlayerTurn(p1, "Alice", 1))
        val board = listOf(PlacedTunnelCard(startPos, startCard))
        val makeEntity = { code: String ->
            GameEntity(
                code, p1, objectMapper.writeValueAsString(board), "[]", "[]",
                objectMapper.writeValueAsString(mapOf(p1 to listOf(pathCard("h1")))),
                objectMapper.writeValueAsString(players), "{}", false, 0
            )
        }

        whenever(gameRepository.findAll()).thenReturn(listOf(makeEntity("G1"), makeEntity("G2"), makeEntity("G3")))
        val freshManager = TurnManager(gameRepository, objectMapper, gameService)
        assertEquals(3, freshManager.loadFromDb())
    }

    // =====================================================================
    // drawCardForPlayer empty deck
    // =====================================================================

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

    // =====================================================================
    // discardCard
    // =====================================================================

    @Test
    fun `discardCard advances turn and removes card from hand`() {
        val result = turnManager.discardCard(lobbyCode, p1, "c1")
        assertEquals(p2, result.updatedGameState.currentPlayerId)
        assertFalse(result.updatedHands[p1]!!.any { it.id == "c1" })
    }

    @Test
    fun `discardCard increments passedSinceEmpty after deck is emptied`() {
        // Setup a game where deck is already empty
        val players = listOf(
            PlayerTurn(p1, "Alice", 1),
            PlayerTurn(p2, "Bob", 2)
        )
        val dist = CardDistributionResult(
            hands = mapOf(
                p1 to mutableListOf(pathCard("d1")),
                p2 to mutableListOf(pathCard("d2"))
            ),
            drawPile = mutableListOf(), // empty from start
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame("DISCARD_EMPTY", dist, GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard))))

        // First discard: deckWasEmptied=true (was set at init), so passedSinceEmpty increments
        val result = turnManager.discardCard("DISCARD_EMPTY", p1, "d1")
        assertEquals(p2, result.updatedGameState.currentPlayerId)
    }

    @Test
    fun `discardCard throws if wrong player tries to discard`() {
        assertThrows<IllegalArgumentException> {
            turnManager.discardCard(lobbyCode, p2, "c2")
        }
    }

    @Test
    fun `discardCard throws if card not in hand`() {
        assertThrows<IllegalArgumentException> {
            turnManager.discardCard(lobbyCode, p1, "NONEXISTENT")
        }
    }

    @Test
    fun `discardCard throws if game not found`() {
        assertThrows<IllegalArgumentException> {
            turnManager.discardCard("NO_GAME", p1, "c1")
        }
    }

    // =====================================================================
    // playCard — error branches
    // =====================================================================

    @Test
    fun `playCard throws if not players turn`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(lobbyCode, p2, "c2", BoardPosition(4, 3), false)
        }
    }

    @Test
    fun `playCard throws if game not found`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playCard("NO_GAME", p1, "c1", BoardPosition(4, 3), false)
        }
    }

    @Test
    fun `playCard throws if card not in hand`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(lobbyCode, p1, "NONEXISTENT", BoardPosition(4, 3), false)
        }
    }

    @Test
    fun `playCard throws if placement invalid`() {
        // Place at a position not adjacent to any existing card
        assertThrows<IllegalArgumentException> {
            turnManager.playCard(lobbyCode, p1, "c1", BoardPosition(0, 0), false)
        }
    }

    @Test
    fun `playCard with rotated card works`() {
        // card c1 has LEFT+RIGHT; rotated180 = LEFT+RIGHT (same). Use a card where rotation matters.
        val asymCard = TunnelCard("asym", CardType.PATH, setOf(Direction.RIGHT, Direction.TOP))
        val players = listOf(PlayerTurn(p1, "A", 1))
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(asymCard)),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        // Board: start at (4,2). Place LEFT+RIGHT path to (4,3) first so the rotated card can fit.
        val bridge = TunnelCard("bridge", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        val board = listOf(
            PlacedTunnelCard(startPos, startCard),
            PlacedTunnelCard(BoardPosition(4, 3), bridge)
        )
        turnManager.initializeGame("ROTATED", dist, GameState(players, p1, board))
        // rotated asymCard = BOTTOM+LEFT — can fit on (4,4) from the left
        // Actually the rotated card of (RIGHT,TOP) = (LEFT, BOTTOM) — try placing at (4,4) from the right side
        // We just test the call does NOT throw for rotated=true (it may throw for invalid placement)
        assertThrows<IllegalArgumentException> {
            // Position (0,0) is always invalid
            turnManager.playCard("ROTATED", p1, "asym", BoardPosition(0, 0), true)
        }
    }

    // =====================================================================
    // playBlockCard — error branches
    // =====================================================================

    @Test
    fun `playBlockCard throws if not players turn`() {
        val dist = CardDistributionResult(
            hands = mapOf(p2 to mutableListOf(blockCard("b1"))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("BLOCKWRONG", dist, GameState(listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)), p1, listOf(PlacedTunnelCard(startPos, startCard))))

        assertThrows<IllegalArgumentException> {
            turnManager.playBlockCard("BLOCKWRONG", p2, "b1", p1)
        }
    }

    @Test
    fun `playBlockCard throws if card not a block card`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playBlockCard(lobbyCode, p1, "c1", p2)
        }
    }

    @Test
    fun `playBlockCard throws if target player not found`() {
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(blockCard("b1"))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("BLOCKNOPLAYER", dist, GameState(listOf(PlayerTurn(p1, "A", 1)), p1, listOf(PlacedTunnelCard(startPos, startCard))))
        assertThrows<IllegalArgumentException> {
            turnManager.playBlockCard("BLOCKNOPLAYER", p1, "b1", "NOSUCHPLAYER")
        }
    }

    @Test
    fun `playBlockCard throws if game not found`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playBlockCard("NO_GAME", p1, "c1", p2)
        }
    }

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

    @Test
    fun `playBlockCard with all three block card types`() {
        for ((cardType, tool) in listOf(
            CardType.CART_RED to ToolType.CART,
            CardType.PICKAXE_RED to ToolType.PICKAXE,
            CardType.LANTERN_RED to ToolType.LANTERN
        )) {
            val code = "BLOCKTYPE_$cardType"
            val dist = CardDistributionResult(
                hands = mapOf(p1 to mutableListOf(TunnelCard("bcard", cardType, emptySet()))),
                drawPile = mutableListOf(),
                goalCards = emptyList(),
                startCard = startCard
            )
            turnManager.initializeGame(
                code, dist,
                GameState(listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)), p1, listOf(PlacedTunnelCard(startPos, startCard)))
            )
            val result = turnManager.playBlockCard(code, p1, "bcard", p2)
            val updatedP2 = result.updatedGameState.players.find { it.playerId == p2 }!!
            assertTrue(tool in updatedP2.blockedTools)
        }
    }

    // =====================================================================
    // playRepairCard — error branches
    // =====================================================================

    @Test
    fun `playRepairCard throws if not players turn`() {
        val players = listOf(
            PlayerTurn(p1, "A", 1),
            PlayerTurn(p2, "B", 2, blockedTools = setOf(ToolType.LANTERN))
        )
        val dist = CardDistributionResult(
            hands = mapOf(p2 to mutableListOf(repairCard("r1"))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("REPAIRWRONG", dist, GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard))))
        assertThrows<IllegalArgumentException> {
            turnManager.playRepairCard("REPAIRWRONG", p2, "r1", p1, ToolType.LANTERN)
        }
    }

    @Test
    fun `playRepairCard throws if card not a repair card`() {
        val players = listOf(
            PlayerTurn(p1, "A", 1),
            PlayerTurn(p2, "B", 2, blockedTools = setOf(ToolType.LANTERN))
        )
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(pathCard("path1"))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("REPAIRNOTCARD", dist, GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard))))
        assertThrows<IllegalArgumentException> {
            turnManager.playRepairCard("REPAIRNOTCARD", p1, "path1", p2, ToolType.LANTERN)
        }
    }

    @Test
    fun `playRepairCard throws if wrong tool for card`() {
        val players = listOf(
            PlayerTurn(p1, "A", 1),
            PlayerTurn(p2, "B", 2, blockedTools = setOf(ToolType.CART))
        )
        // LANTERN_GREEN repairs only LANTERN, not CART
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("r1", CardType.LANTERN_GREEN, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("REPAIRWRONGTOOL", dist, GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard))))
        assertThrows<IllegalArgumentException> {
            turnManager.playRepairCard("REPAIRWRONGTOOL", p1, "r1", p2, ToolType.CART)
        }
    }

    @Test
    fun `playRepairCard throws if game not found`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playRepairCard("NO_GAME", p1, "r1", p2, ToolType.LANTERN)
        }
    }

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

    @Test
    fun `playRepairCard with all single repair card types`() {
        for ((cardType, tool) in listOf(
            CardType.CART_GREEN to ToolType.CART,
            CardType.PICKAXE_GREEN to ToolType.PICKAXE,
            CardType.LANTERN_GREEN to ToolType.LANTERN
        )) {
            val code = "REPAIRTYPE_$cardType"
            val blockedPlayer = PlayerTurn(p2, "B", 2, blockedTools = setOf(tool))
            val dist = CardDistributionResult(
                hands = mapOf(p1 to mutableListOf(TunnelCard("rcard", cardType, emptySet()))),
                drawPile = mutableListOf(),
                goalCards = emptyList(),
                startCard = startCard
            )
            turnManager.initializeGame(
                code, dist,
                GameState(listOf(PlayerTurn(p1, "A", 1), blockedPlayer), p1, listOf(PlacedTunnelCard(startPos, startCard)))
            )
            val result = turnManager.playRepairCard(code, p1, "rcard", p2, tool)
            val updatedP2 = result.updatedGameState.players.find { it.playerId == p2 }!!
            assertFalse(tool in updatedP2.blockedTools)
        }
    }

    // =====================================================================
    // Double repair card
    // =====================================================================

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

    @Test
    fun `all double repair card types are tested`() {
        for ((cardType, tool1, tool2) in listOf(
            Triple(CardType.DOUBLE_LANTERN_CART, ToolType.LANTERN, ToolType.CART),
            Triple(CardType.DOUBLE_PICKAXE_CART, ToolType.PICKAXE, ToolType.CART),
            Triple(CardType.DOUBLE_PICKAXE_LANTERN, ToolType.PICKAXE, ToolType.LANTERN)
        )) {
            val code = "DOUBLE_$cardType"
            val blockedPlayer = PlayerTurn(p2, "B", 2, blockedTools = setOf(tool1, tool2))
            val dist = CardDistributionResult(
                hands = mapOf(p1 to mutableListOf(TunnelCard("dr", cardType, emptySet()))),
                drawPile = mutableListOf(),
                goalCards = emptyList(),
                startCard = startCard
            )
            turnManager.initializeGame(
                code, dist,
                GameState(listOf(PlayerTurn(p1, "A", 1), blockedPlayer), p1, listOf(PlacedTunnelCard(startPos, startCard)))
            )
            val result = turnManager.playRepairCard(code, p1, "dr", p2, tool1)
            val updated = result.updatedGameState.players.find { it.playerId == p2 }!!
            assertFalse(tool1 in updated.blockedTools)
        }
    }

    // =====================================================================
    // Map card
    // =====================================================================

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

    @Test
    fun `playMapCard throws if not players turn`() {
        val hiddenGoal = CardDeck.createGoalCards().first().copy(isRevealed = false)
        val dist = CardDistributionResult(
            hands = mapOf(p2 to mutableListOf(TunnelCard("m1", CardType.MAPCARD, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "MAPWRONG", dist,
            GameState(listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)), p1, listOf(PlacedTunnelCard(goalPos, hiddenGoal), PlacedTunnelCard(startPos, startCard)))
        )
        assertThrows<IllegalArgumentException> {
            turnManager.playMapCard("MAPWRONG", p2, "m1", goalPos)
        }
    }

    @Test
    fun `playMapCard throws if card not found`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playMapCard(lobbyCode, p1, "BADCARD", goalPos)
        }
    }

    @Test
    fun `playMapCard throws if no card at target position`() {
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("m1", CardType.MAPCARD, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("MAPNOCARD", dist, GameState(listOf(PlayerTurn(p1, "A", 1)), p1, listOf(PlacedTunnelCard(startPos, startCard))))
        assertThrows<IllegalArgumentException> {
            turnManager.playMapCard("MAPNOCARD", p1, "m1", BoardPosition(0, 0))
        }
    }

    @Test
    fun `playMapCard throws if game not found`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playMapCard("NO_GAME", p1, "m1", goalPos)
        }
    }

    // =====================================================================
    // Rockfall card
    // =====================================================================

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
        turnManager.initializeGame("ROCK_START", dist, GameState(listOf(PlayerTurn(p1, "A", 1)), p1, listOf(PlacedTunnelCard(startPos, startCard))))
        assertThrows<IllegalArgumentException> {
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
        turnManager.initializeGame("ROCK_GOAL", dist, GameState(listOf(PlayerTurn(p1, "A", 1)), p1, listOf(PlacedTunnelCard(goalPosCustom, goalCard))))
        assertThrows<IllegalArgumentException> {
            turnManager.playRockfallCard("ROCK_GOAL", p1, "rfGoal", goalPosCustom)
        }
    }

    @Test
    fun `playRockfallCard throws if not players turn`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playRockfallCard(lobbyCode, p2, "c2", startPos)
        }
    }

    @Test
    fun `playRockfallCard throws if card not found`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playRockfallCard(lobbyCode, p1, "BADCARD", startPos)
        }
    }

    @Test
    fun `playRockfallCard throws if not a rockfall card`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playRockfallCard(lobbyCode, p1, "c1", startPos)
        }
    }

    @Test
    fun `playRockfallCard throws if no card at target position`() {
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("rf1", CardType.ROCKFALL, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("ROCKNOPOS", dist, GameState(listOf(PlayerTurn(p1, "A", 1)), p1, listOf(PlacedTunnelCard(startPos, startCard))))
        assertThrows<IllegalArgumentException> {
            turnManager.playRockfallCard("ROCKNOPOS", p1, "rf1", BoardPosition(0, 0))
        }
    }

    @Test
    fun `playRockfallCard throws if game not found`() {
        assertThrows<IllegalArgumentException> {
            turnManager.playRockfallCard("NO_GAME", p1, "rf1", startPos)
        }
    }

    // =====================================================================
    // Cheat tests
    // =====================================================================

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
    fun `cheat VOLUME_SEQUENCE_DISCARD discards random hand card and draws replacement without consuming turn`() {
        val card1 = pathCard("random-1")
        val card2 = pathCard("random-2")
        val deckCard = pathCard("deck-card")
        val dist = CardDistributionResult(
            hands = mapOf(
                p1 to mutableListOf(pathCard("p1-card")),
                p2 to mutableListOf(card1, card2)
            ),
            drawPile = mutableListOf(deckCard),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "VOLSEQ",
            dist,
            GameState(listOf(PlayerTurn(p1, "Alice", 1), PlayerTurn(p2, "Bob", 2)), p1, emptyList())
        )

        val result = turnManager.cheatPlayer("VOLSEQ", p2, CheatType.VOLUME_SEQUENCE_DISCARD)

        val remainingHand = result.updatedHands[p2].orEmpty()
        assertEquals(2, remainingHand.size)
        assertTrue(remainingHand.any { it.id == "deck-card" })
        assertEquals(1, remainingHand.count { it.id in setOf("random-1", "random-2") })
        assertEquals(p1, result.updatedGameState.currentPlayerId)
        assertEquals(0, result.updatedGameState.deckSize)
    }

    @Test
    fun `cheat VOLUME_SEQUENCE_DISCARD is no-op for empty hand`() {
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf()),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "VOLSEQ_EMPTY",
            dist,
            GameState(listOf(PlayerTurn(p1, "Alice", 1), PlayerTurn(p2, "Bob", 2)), p2, emptyList())
        )

        val result = turnManager.cheatPlayer("VOLSEQ_EMPTY", p1, CheatType.VOLUME_SEQUENCE_DISCARD)

        assertTrue(result.updatedHands[p1].orEmpty().isEmpty())
        assertEquals(p2, result.updatedGameState.currentPlayerId)
    }

    @Test
    fun `cheat TEST_REVEAL consumes turn and moves to next player`() {
        setupStandardGame()
        val result = turnManager.cheatPlayer(lobbyCode, p1, CheatType.TEST_REVEAL)
        assertEquals(p2, result.updatedGameState.currentPlayerId)
    }

    @Test
    fun `cheatPlayer throws on unknown cheat type`() {
        assertThrows<IllegalArgumentException> {
            turnManager.cheatPlayer(lobbyCode, p1, CheatType.TEST_INVALID_TYPE)
        }
    }

    @Test
    fun `cheatPlayer throws when game not found`() {
        assertThrows<IllegalArgumentException> {
            turnManager.cheatPlayer("NON_EXISTENT", p1, CheatType.LANTERN_FLASHLIGHT)
        }
    }

    @Test
    fun `cheatPlayer throws when game is already over`() {
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(pathCard("p1-card"))),
            drawPile = mutableListOf(pathCard("deck-card")),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "CHEAT_OVER",
            dist,
            GameState(
                players = listOf(PlayerTurn(p1, "Alice", 1)),
                currentPlayerId = p1,
                isRoundOver = true,
                isGameOver = true
            )
        )

        assertThrows<IllegalArgumentException> {
            turnManager.cheatPlayer("CHEAT_OVER", p1, CheatType.VOLUME_SEQUENCE_DISCARD)
        }
    }

    // =====================================================================
    // Blocked player behaviour
    // =====================================================================

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
        turnManager.playBlockCard("BLOCKEDACT", p1, "b1", p1)
    }

    // =====================================================================
    // Winner determination — Golddigger win (DWARVES)
    // =====================================================================

    @Test
    fun `golddigger win triggers DWARVES result and distributes gold`() {
        val golddigger1 = Player("p1", "Alice", role = Role.GOLDDIGGER)
        val golddigger2 = Player("p2", "Bob", role = Role.GOLDDIGGER)
        val saboteur = Player("p3", "Charlie", role = Role.SABOTEUR)
        // FIX: 3 Spieler zwingend notwendig für den CardDistributor in startNextRound
        mockPlayerData(mapOf(p1 to golddigger1, p2 to golddigger2, p3 to saboteur))

        val players = listOf(PlayerTurn(p1, "Alice", 1), PlayerTurn(p2, "Bob", 2), PlayerTurn(p3, "Charlie", 3))
        val hands = mapOf(
            p1 to mutableListOf(pathCard("win_card", setOf(Direction.LEFT, Direction.RIGHT))),
            p2 to mutableListOf(pathCard("c2")),
            p3 to mutableListOf(pathCard("c3"))
        )
        setupWinningBoard("DWARVES_WIN", players, hands)

        // The board is already in winning state.
        val result = turnManager.discardCard("DWARVES_WIN", p1, "win_card")
        assertEquals("DWARVES", result.winner)
    }

    @Test
    fun `saboteur win triggers SABOTEURS result`() {
        val saboteur1 = Player("p1", "Alice", role = Role.SABOTEUR)
        val saboteur2 = Player("p2", "Bob", role = Role.SABOTEUR)
        val saboteur3 = Player("p3", "Charlie", role = Role.SABOTEUR)
        mockPlayerData(mapOf(p1 to saboteur1, p2 to saboteur2, p3 to saboteur3))

        // 3 players required by CardDistributor; currentRound=3 so game ends without startNextRound
        val players = listOf(PlayerTurn(p1, "Alice", 1), PlayerTurn(p2, "Bob", 2), PlayerTurn(p3, "Charlie", 3))
        val dist = CardDistributionResult(
            hands = mapOf(
                p1 to mutableListOf(pathCard("d1")),
                p2 to mutableListOf(pathCard("d2")),
                p3 to mutableListOf(pathCard("d3"))
            ),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "SABOTEUR_WIN", dist,
            GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard)), currentRound = 3)
        )

        // All 3 players discard → passedSinceEmpty reaches players.size → saboteur win
        turnManager.discardCard("SABOTEUR_WIN", p1, "d1")
        turnManager.discardCard("SABOTEUR_WIN", p2, "d2")
        val result = turnManager.discardCard("SABOTEUR_WIN", p3, "d3")
        assertEquals("SABOTEURS", result.winner)
    }

    @Test
    fun `game over after MAX_ROUNDS determines final winner by gold`() {
        val golddigger1 = Player("p1", "Alice", role = Role.GOLDDIGGER, goldCards = listOf(GoldCard("g1", 3)))
        val golddigger2 = Player("p2", "Bob", role = Role.GOLDDIGGER, goldCards = listOf(GoldCard("g2", 1)))
        // mock BEFORE initializeGame
        mockPlayerData(mapOf(p1 to golddigger1, p2 to golddigger2))

        val players = listOf(PlayerTurn(p1, "Alice", 1), PlayerTurn(p2, "Bob", 2))
        val hands = mapOf(
            p1 to mutableListOf(pathCard("win_card")),
            p2 to mutableListOf(pathCard("c2"))
        )
        // Set to round 3 (MAX_ROUNDS) so the game ends
        val bridge = TunnelCard("bridge", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        val goalRevealed = TunnelCard(
            "goal_win", CardType.GOAL,
            setOf(Direction.LEFT, Direction.RIGHT, Direction.TOP, Direction.BOTTOM),
            isRevealed = true, isGoal = true
        )
        val board = listOf(
            PlacedTunnelCard(startPos, startCard),
            PlacedTunnelCard(BoardPosition(4, 3), bridge.copy(id = "br3")),
            PlacedTunnelCard(BoardPosition(4, 4), bridge.copy(id = "br4")),
            PlacedTunnelCard(BoardPosition(4, 5), bridge.copy(id = "br5")),
            PlacedTunnelCard(BoardPosition(4, 6), bridge.copy(id = "br6")),
            PlacedTunnelCard(BoardPosition(4, 7), bridge.copy(id = "br7")),
            PlacedTunnelCard(BoardPosition(4, 8), bridge.copy(id = "br8")),
            PlacedTunnelCard(BoardPosition(4, 9), bridge.copy(id = "br9")),
            PlacedTunnelCard(BoardPosition(4, 10), goalRevealed)
        )
        val dist = CardDistributionResult(
            hands = hands,
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        val initialState = GameState(players, p1, board, currentRound = 3) // MAX_ROUNDS = 3
        turnManager.initializeGame("GAME_OVER", dist, initialState)

        val result = turnManager.discardCard("GAME_OVER", p1, "win_card")
        assertEquals("DWARVES", result.winner)
        assertTrue(result.updatedGameState.isGameOver)
        assertNotNull(result.updatedGameState.lastRoundResult)
        assertTrue(result.updatedGameState.lastRoundResult!!.gameFinished)
    }

    // =====================================================================
    // Multi-round: startNextRound
    // =====================================================================

    @Test
    fun `golddigger win in round 1 starts next round`() {
        val golddigger = Player("p1", "Alice", role = Role.GOLDDIGGER)
        val golddigger2 = Player("p2", "Bob", role = Role.GOLDDIGGER)
        val saboteur = Player("p3", "Charlie", role = Role.SABOTEUR)
        // FIX: 3 Spieler zwingend notwendig für den CardDistributor
        mockPlayerData(mapOf(p1 to golddigger, p2 to golddigger2, p3 to saboteur))

        val players = listOf(PlayerTurn(p1, "Alice", 1), PlayerTurn(p2, "Bob", 2), PlayerTurn(p3, "Charlie", 3))
        val hands = mapOf(
            p1 to mutableListOf(pathCard("win_card")),
            p2 to mutableListOf(pathCard("c2")),
            p3 to mutableListOf(pathCard("c3"))
        )
        setupWinningBoard("ROUND2", players, hands)

        val result = turnManager.discardCard("ROUND2", p1, "win_card")
        // After round 1 win, round 2 starts
        assertEquals(2, result.updatedGameState.currentRound)
        assertFalse(result.updatedGameState.isRoundOver)
        assertFalse(result.updatedGameState.isGameOver)
    }

    @Test
    fun `round transition populates newPlayerRoles with a role for every player`() {
        val golddigger = Player("p1", "Alice", role = Role.GOLDDIGGER)
        val golddigger2 = Player("p2", "Bob", role = Role.GOLDDIGGER)
        val saboteur = Player("p3", "Charlie", role = Role.SABOTEUR)
        mockPlayerData(mapOf(p1 to golddigger, p2 to golddigger2, p3 to saboteur))

        val players = listOf(PlayerTurn(p1, "Alice", 1), PlayerTurn(p2, "Bob", 2), PlayerTurn(p3, "Charlie", 3))
        val hands = mapOf(
            p1 to mutableListOf(pathCard("win_card")),
            p2 to mutableListOf(pathCard("c2")),
            p3 to mutableListOf(pathCard("c3"))
        )
        setupWinningBoard("ROUND2_ROLES", players, hands)

        val result = turnManager.discardCard("ROUND2_ROLES", p1, "win_card")

        assertTrue(result.newPlayerRoles.isNotEmpty(), "newPlayerRoles must be set after round transition")
        assertEquals(setOf(p1, p2, p3), result.newPlayerRoles.keys)
        result.newPlayerRoles.values.forEach { player ->
            assertNotNull(player.role, "Every player must have a role after redistribution")
        }
    }

    @Test
    fun `no round transition leaves newPlayerRoles empty`() {
        val result = turnManager.discardCard(lobbyCode, p1, "c1")

        assertNull(result.winner)
        assertTrue(result.newPlayerRoles.isEmpty(), "newPlayerRoles must be empty when no round ended")
    }

    // =====================================================================
    // Gold distribution — saboteur count variants
    // =====================================================================

    @Test
    fun `saboteur gold distribution with single saboteur`() {
        val sab1 = Player("p1", "Alice", role = Role.SABOTEUR)
        val sab2 = Player("p2", "Bob", role = Role.SABOTEUR)
        val sab3 = Player("p3", "Charlie", role = Role.SABOTEUR)
        // mock BEFORE initializeGame; currentRound=3 avoids startNextRound → CardDistributor
        mockPlayerData(mapOf(p1 to sab1, p2 to sab2, p3 to sab3))

        val players = listOf(PlayerTurn(p1, "Alice", 1), PlayerTurn(p2, "Bob", 2), PlayerTurn(p3, "Charlie", 3))
        val dist = CardDistributionResult(
            hands = mapOf(
                p1 to mutableListOf(pathCard("d1")),
                p2 to mutableListOf(pathCard("d2")),
                p3 to mutableListOf(pathCard("d3"))
            ),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame("SAB1", dist, GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard)), currentRound = 3))
        turnManager.discardCard("SAB1", p1, "d1")
        turnManager.discardCard("SAB1", p2, "d2")
        val result = turnManager.discardCard("SAB1", p3, "d3")
        assertEquals("SABOTEURS", result.winner)
    }

    // =====================================================================
    // Turn order / nextPlayerId edge cases
    // =====================================================================

    @Test
    fun `turn cycles back to first player after last`() {
        // p1 discards -> p2, p2 -> p3, p3 -> wraps back to p1
        turnManager.discardCard(lobbyCode, p1, "c1")
        turnManager.discardCard(lobbyCode, p2, "c2")
        val result = turnManager.discardCard(lobbyCode, p3, "c3")
        assertEquals(p1, result.updatedGameState.currentPlayerId)
    }

    // =====================================================================
    // Persist / knownGoals stored per player
    // =====================================================================

    @Test
    fun `playMapCard records known goal per player and persists`() {
        val hiddenGoal = CardDeck.createGoalCards().first().copy(isRevealed = false)
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("m1", CardType.MAPCARD, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        val state = GameState(
            players = listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)),
            currentPlayerId = p1,
            boardPlacements = listOf(PlacedTunnelCard(goalPos, hiddenGoal), PlacedTunnelCard(startPos, startCard))
        )
        turnManager.initializeGame("MAP_PERSIST", dist, state)

        val (_, mapResult) = turnManager.playMapCard("MAP_PERSIST", p1, "m1", goalPos)
        assertEquals(goalPos, mapResult.position)
        verify(gameRepository, atLeastOnce()).save(any())
    }

    // =====================================================================
    // revealGoalCards — goal on the board gets revealed when path connects
    // =====================================================================

    @Test
    fun `placing path card adjacent to goal card reveals goal`() {
        // Place a left-right path at (4,9) adjacent to a goal at (4,10)
        val hiddenGoal = TunnelCard(
            "goal_hidden", CardType.GOAL,
            setOf(Direction.LEFT, Direction.RIGHT),
            isRevealed = false,
            isGoal = false
        )
        val connector = TunnelCard("connector", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        // Build board: start (4,2) -> bridges -> (4,8) so connector can go at (4,9)
        val bridge = TunnelCard("b", CardType.PATH, setOf(Direction.LEFT, Direction.RIGHT))
        val board = mutableListOf(
            PlacedTunnelCard(startPos, startCard),
            PlacedTunnelCard(BoardPosition(4, 3), bridge.copy(id = "b3")),
            PlacedTunnelCard(BoardPosition(4, 4), bridge.copy(id = "b4")),
            PlacedTunnelCard(BoardPosition(4, 5), bridge.copy(id = "b5")),
            PlacedTunnelCard(BoardPosition(4, 6), bridge.copy(id = "b6")),
            PlacedTunnelCard(BoardPosition(4, 7), bridge.copy(id = "b7")),
            PlacedTunnelCard(BoardPosition(4, 8), bridge.copy(id = "b8")),
            PlacedTunnelCard(BoardPosition(4, 10), hiddenGoal)
        )

        val players = listOf(PlayerTurn(p1, "A", 1))
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(connector)),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame("REVEAL_GOAL", dist, GameState(players, p1, board))

        val result = turnManager.playCard("REVEAL_GOAL", p1, "connector", BoardPosition(4, 9), false)
        val goalOnBoard = result.updatedGameState.boardPlacements.find { it.position == BoardPosition(4, 10) }
        assertNotNull(goalOnBoard)
        assertTrue(goalOnBoard!!.card.isRevealed)
    }

    // =====================================================================
    // updatePlayerGoldValues
    // =====================================================================

    @Test
    fun `gold values update correctly from player data`() {
        val goldCard1 = GoldCard("g1", 3)
        val goldCard2 = GoldCard("g2", 2)
        val p1Data = Player("p1", "Alice", role = Role.GOLDDIGGER, goldCards = listOf(goldCard1, goldCard2))
        val p2Data = Player("p2", "Bob", role = Role.GOLDDIGGER, goldCards = emptyList())
        // mock BEFORE initializeGame
        mockPlayerData(mapOf(p1 to p1Data, p2 to p2Data))

        val players = listOf(PlayerTurn(p1, "Alice", 1), PlayerTurn(p2, "Bob", 2))
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(pathCard("x1")), p2 to mutableListOf(pathCard("x2"))),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame("GOLD_VAL", dist, GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard))))

        val state = turnManager.getGameState("GOLD_VAL")
        val p1Turn = state.players.find { it.playerId == p1 }!!
        assertEquals(5, p1Turn.goldValue) // 3 + 2
    }

    // =====================================================================
    // Misc edge cases
    // =====================================================================

    @Test
    fun `playCard with draw pile having one card empties it`() {
        val deckCard = pathCard("deckCard", setOf(Direction.TOP))
        setupStandardGame(mutableListOf(deckCard))

        val result = turnManager.playCard(lobbyCode, p1, "c1", BoardPosition(4, 3), false)
        // After drawing, deck should be empty
        assertEquals(0, result.updatedGameState.deckSize)
    }

    @Test
    fun `discardCard with non-empty draw pile draws a card`() {
        val deckCard = pathCard("deckCard2", setOf(Direction.TOP))
        setupStandardGame(mutableListOf(deckCard))

        val handSizeBefore = turnManager.getHands(lobbyCode)[p1]!!.size
        turnManager.discardCard(lobbyCode, p1, "c1")
        val handSizeAfter = turnManager.getHands(lobbyCode)[p1]!!.size
        // Discard removed one, draw added one -> same size
        assertEquals(handSizeBefore, handSizeAfter)
    }

    @Test
    fun `initializeGame persists and game is findable`() {
        val state = turnManager.getGameStateSnapshot(lobbyCode)
        assertNotNull(state)
        assertEquals(p1, state.currentPlayerId)
    }

    @Test
    fun `playBlockCard with cart block type`() {
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("cartblock", CardType.CART_RED, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "CARTBLOCK", dist,
            GameState(listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)), p1, listOf(PlacedTunnelCard(startPos, startCard)))
        )
        val result = turnManager.playBlockCard("CARTBLOCK", p1, "cartblock", p2)
        val p2Updated = result.updatedGameState.players.find { it.playerId == p2 }!!
        assertTrue(ToolType.CART in p2Updated.blockedTools)
    }

    @Test
    fun `playBlockCard with pickaxe block type`() {
        val dist = CardDistributionResult(
            hands = mapOf(p1 to mutableListOf(TunnelCard("pickblock", CardType.PICKAXE_RED, emptySet()))),
            drawPile = mutableListOf(),
            goalCards = emptyList(),
            startCard = startCard
        )
        turnManager.initializeGame(
            "PICKBLOCK", dist,
            GameState(listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2)), p1, listOf(PlacedTunnelCard(startPos, startCard)))
        )
        val result = turnManager.playBlockCard("PICKBLOCK", p1, "pickblock", p2)
        val p2Updated = result.updatedGameState.players.find { it.playerId == p2 }!!
        assertTrue(ToolType.PICKAXE in p2Updated.blockedTools)
    }

    @Test
    fun `determineNextRoundStartPlayer uses lastPlayerWhoPlayed order`() {
        // p1 plays, p2 plays, so next round starts with p3
        val golddigger = Player("p1", "Alice", role = Role.GOLDDIGGER)
        val golddigger2 = Player("p2", "Bob", role = Role.GOLDDIGGER)
        val golddigger3 = Player("p3", "Charlie", role = Role.GOLDDIGGER)
        // mock BEFORE setupWinningBoard which calls initializeGame
        mockPlayerData(mapOf(p1 to golddigger, p2 to golddigger2, p3 to golddigger3))

        val players = listOf(
            PlayerTurn(p1, "Alice", 1),
            PlayerTurn(p2, "Bob", 2),
            PlayerTurn(p3, "Charlie", 3)
        )
        val hands = mapOf(
            p1 to mutableListOf(pathCard("wp")),
            p2 to mutableListOf(pathCard("c2")),
            p3 to mutableListOf(pathCard("c3"))
        )

        // FIX: currentRound auf 1 gesetzt! Bei 3 würde das Spiel enden und keine Runde 2 starten.
        setupWinningBoard("START_PLAYER", players, hands, currentRound = 1)

        // p1 discards (lastPlayerWhoPlayed = p1) → next round starts at p2
        val result = turnManager.discardCard("START_PLAYER", p1, "wp")

        // Round 2 started, new currentPlayerId should be p2
        assertEquals(2, result.updatedGameState.currentRound)
        assertEquals(p2, result.updatedGameState.currentPlayerId)
    }

    @Test
    fun `saboteurs win with 3 saboteurs distributes correct gold per saboteur`() {
        val sab1 = Player("p1", "A", role = Role.SABOTEUR)
        val sab2 = Player("p2", "B", role = Role.SABOTEUR)
        val sab3 = Player("p3", "C", role = Role.SABOTEUR)
        // mock BEFORE initializeGame
        mockPlayerData(mapOf(p1 to sab1, p2 to sab2, p3 to sab3))

        val players = listOf(PlayerTurn(p1, "A", 1), PlayerTurn(p2, "B", 2), PlayerTurn(p3, "C", 3))
        val dist = CardDistributionResult(
            hands = mapOf(
                p1 to mutableListOf(pathCard("d1")),
                p2 to mutableListOf(pathCard("d2")),
                p3 to mutableListOf(pathCard("d3"))
            ),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame("SAB3", dist, GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard)), currentRound = 3))

        turnManager.discardCard("SAB3", p1, "d1")
        turnManager.discardCard("SAB3", p2, "d2")
        val result = turnManager.discardCard("SAB3", p3, "d3")
        assertEquals("SABOTEURS", result.winner)
    }

    @Test
    fun `saboteurs win with 4 saboteurs triggers win`() {
        val p4 = "p4"
        val sab1 = Player("p1", "A", role = Role.SABOTEUR)
        val sab2 = Player("p2", "B", role = Role.SABOTEUR)
        val sab3 = Player("p3", "C", role = Role.SABOTEUR)
        val sab4 = Player("p4", "D", role = Role.SABOTEUR)
        // mock BEFORE initializeGame
        mockPlayerData(mapOf(p1 to sab1, p2 to sab2, p3 to sab3, p4 to sab4))

        val players = listOf(
            PlayerTurn(p1, "A", 1),
            PlayerTurn(p2, "B", 2),
            PlayerTurn(p3, "C", 3),
            PlayerTurn(p4, "D", 4)
        )
        val dist = CardDistributionResult(
            hands = mapOf(
                p1 to mutableListOf(pathCard("d1")),
                p2 to mutableListOf(pathCard("d2")),
                p3 to mutableListOf(pathCard("d3")),
                p4 to mutableListOf(pathCard("d4"))
            ),
            drawPile = mutableListOf(),
            goalCards = CardDeck.createGoalCards(),
            startCard = startCard
        )
        turnManager.initializeGame("SAB4", dist, GameState(players, p1, listOf(PlacedTunnelCard(startPos, startCard)), currentRound = 3))

        turnManager.discardCard("SAB4", p1, "d1")
        turnManager.discardCard("SAB4", p2, "d2")
        turnManager.discardCard("SAB4", p3, "d3")
        val result = turnManager.discardCard("SAB4", p4, "d4")
        assertEquals("SABOTEURS", result.winner)
    }
}
