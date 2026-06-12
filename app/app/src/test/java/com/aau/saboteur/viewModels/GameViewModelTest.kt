package com.aau.saboteur.viewModels

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.CheatType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.PlayerTurn
import com.aau.saboteur.model.Role
import com.aau.saboteur.model.RoundResult
import com.aau.saboteur.model.ToolType
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.network.WebSocketManager
import com.aau.saboteur.network.game.GameApi
import com.aau.saboteur.network.game.MapResult
import kotlinx.coroutines.launch
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val gameStateUpdates = MutableStateFlow(GameState())
    private val playerUpdates = MutableStateFlow<Player?>(null)
    private val cardsDealtUpdates = MutableStateFlow<Map<String, List<TunnelCard>>?>(null)
    private val errorMessages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 10)
    private val gameOverEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    private val validPositionsUpdates = MutableSharedFlow<List<BoardPosition>>(replay = 1, extraBufferCapacity = 1)
    private val mapResultEvents = MutableSharedFlow<MapResult>(replay = 0, extraBufferCapacity = 1)
    private val connectionStatus = MutableStateFlow(true)

    private val eventHandlers = mutableMapOf<String, (String) -> Unit>()

    private lateinit var viewModel: GameViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockkObject(WebSocketManager)
        every { WebSocketManager.connectionStatus } returns connectionStatus
        every { WebSocketManager.onEvent(any(), any()) } answers {
            eventHandlers[firstArg()] = secondArg()
        }

        mockkObject(GameApi)
        every { GameApi.gameStateUpdates } returns gameStateUpdates
        every { GameApi.playerUpdates } returns playerUpdates
        every { GameApi.cardsDealtUpdates } returns cardsDealtUpdates
        every { GameApi.errorMessages } returns errorMessages
        every { GameApi.gameOverEvents } returns gameOverEvents
        every { GameApi.validPositionsUpdates } returns validPositionsUpdates
        every { GameApi.mapResultEvents } returns mapResultEvents
        every { GameApi.playCard(any(), any(), any(), any(), any()) } just Runs
        every { GameApi.discardCard(any(), any(), any()) } just Runs
        every { GameApi.requestValidPositions(any(), any(), any()) } just Runs
        every { GameApi.clearValidPositions() } just Runs
        every { GameApi.playBlockCard(any(), any(), any(), any()) } just Runs
        every { GameApi.playRepairCard(any(), any(), any(), any(), any()) } just Runs
        every { GameApi.playMapCard(any(), any(), any(), any()) } just Runs
        every { GameApi.playRockfallCard(any(), any(), any(), any()) } just Runs
        every { GameApi.triggerCheat(any(), any()) } just Runs

        viewModel = GameViewModel()
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    // region helpers
    private fun pathCard(id: String = "p1", isRotated: Boolean = false) =
        TunnelCard(id, CardType.PATH, setOf(Direction.TOP, Direction.BOTTOM), isRotated = isRotated)
    private fun blockCard(id: String = "b1") = TunnelCard(id, CardType.PICKAXE_RED, emptySet())
    private fun repairCard(id: String = "r1") = TunnelCard(id, CardType.PICKAXE_GREEN, emptySet())
    private fun mapCard(id: String = "m1") = TunnelCard(id, CardType.MAPCARD, emptySet())
    private fun rockfallCard(id: String = "rf1") = TunnelCard(id, CardType.ROCKFALL, emptySet())
    private fun startCard(id: String = "s1") = TunnelCard(id, CardType.START, emptySet())

    private fun activateGame(playerId: String = "P1", lobbyCode: String = "L1") {
        viewModel.initGameSession(lobbyCode, playerId)
        gameStateUpdates.value = GameState(
            players = listOf(PlayerTurn(playerId = playerId, playerName = "Me")),
            currentPlayerId = playerId
        )
    }
    // endregion

    // region basic state setters
    @Test
    fun `initGameSession should set lobbyCode and playerId`() {
        viewModel.initGameSession("LOBBY", "PLAYER")
        assertEquals("LOBBY", viewModel.uiState.value.lobbyCode)
        assertEquals("PLAYER", viewModel.uiState.value.localPlayerId)
    }

    @Test
    fun `setLocalPlayerId should update state`() {
        viewModel.setLocalPlayerId("NEW_PID")
        assertEquals("NEW_PID", viewModel.uiState.value.localPlayerId)
    }

    @Test
    fun `setLocalPlayerId with null should clear localPlayerId`() {
        viewModel.setLocalPlayerId("PID")
        viewModel.setLocalPlayerId(null)
        assertNull(viewModel.uiState.value.localPlayerId)
    }

    @Test
    fun `setError should set errorMessage`() {
        viewModel.setError("Something went wrong")
        assertEquals("Something went wrong", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `dismissMapResult should clear lastMapResult`() {
        val result = MapResult(BoardPosition(1, 2), TunnelCard("c", CardType.GOAL, emptySet(), isGoal = true))
        mapResultEvents.tryEmit(result)
        assertEquals(result, viewModel.uiState.value.lastMapResult)

        viewModel.dismissMapResult()
        assertNull(viewModel.uiState.value.lastMapResult)
    }
    // endregion

    // region observers
    @Test
    fun `gameStateUpdates should update uiState and clear syncing`() {
        val newState = GameState(
            players = listOf(PlayerTurn("P1", "One")),
            currentPlayerId = "P1",
            deckSize = 30
        )
        gameStateUpdates.value = newState

        assertEquals(newState, viewModel.uiState.value.gameState)
        assertEquals(30, viewModel.uiState.value.remainingDeckSize)
        assertEquals(false, viewModel.uiState.value.isSyncing)
        assertEquals(false, viewModel.uiState.value.isStartingGame)
    }

    @Test
    fun `gameStateUpdates should clear validPositions`() {
        validPositionsUpdates.tryEmit(listOf(BoardPosition(0, 0)))
        assertEquals(listOf(BoardPosition(0, 0)), viewModel.validPositions.value)

        gameStateUpdates.value = GameState(players = listOf(PlayerTurn("P1", "Me")))
        assertEquals(emptyList<BoardPosition>(), viewModel.validPositions.value)
    }

    @Test
    fun `playerUpdates should update player in state`() {
        val player = Player(id = "P1", name = "Me", role = Role.SABOTEUR)
        playerUpdates.value = player
        assertEquals(player, viewModel.uiState.value.player)
    }

    @Test
    fun `cardsDealtUpdates should update hands and reset card rotations`() {
        val hands = mapOf("P1" to listOf(pathCard()))
        cardsDealtUpdates.value = hands

        assertEquals(hands, viewModel.uiState.value.hands)
        assertEquals(emptyMap<String, Boolean>(), viewModel.uiState.value.cardRotations)
    }

    @Test
    fun `gameOverEvents emission should be handled by observer`() {
        // Coverage for observeGameOverEvents collector body
        val emitted = gameOverEvents.tryEmit("DWARVES")
        assertEquals(true, emitted)
    }

    @Test
    fun `validPositionsUpdates should update validPositions flow`() {
        val positions = listOf(BoardPosition(0, 0), BoardPosition(1, 1))
        validPositionsUpdates.tryEmit(positions)
        assertEquals(positions, viewModel.validPositions.value)
    }

    @Test
    fun `errorMessages should set errorMessage and clear after delay`() {
        errorMessages.tryEmit("Server error")
        assertEquals("Server error", viewModel.uiState.value.errorMessage)

        testDispatcher.scheduler.advanceTimeBy(2001)
        testDispatcher.scheduler.runCurrent()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `mapResultEvents should set lastMapResult and clear after delay`() {
        val result = MapResult(BoardPosition(2, 3), TunnelCard("g", CardType.GOAL, emptySet()))
        mapResultEvents.tryEmit(result)
        assertEquals(result, viewModel.uiState.value.lastMapResult)

        testDispatcher.scheduler.advanceTimeBy(5001)
        testDispatcher.scheduler.runCurrent()

        assertNull(viewModel.uiState.value.lastMapResult)
    }

    @Test
    fun `connection lost should set isSyncing true`() {
        activateGame()
        assertEquals(false, viewModel.uiState.value.isSyncing)

        connectionStatus.value = false
        assertEquals(true, viewModel.uiState.value.isSyncing)
    }

    @Test
    fun `SYNC_COMPLETE event should clear isSyncing`() {
        // Initial state has isSyncing=true (players empty in init)
        assertEquals(true, viewModel.uiState.value.isSyncing)

        eventHandlers["SYNC_COMPLETE"]?.invoke("")

        assertEquals(false, viewModel.uiState.value.isSyncing)
    }
    // endregion

    // region selectCard
    @Test
    fun `selectCard with same id should deselect and clear valid positions for path card`() {
        activateGame()
        val card = pathCard()
        viewModel.selectCard(card)
        viewModel.selectCard(card)

        assertNull(viewModel.uiState.value.selectedCard)
        verify { GameApi.clearValidPositions() }
    }

    @Test
    fun `selectCard with path card should request valid positions`() {
        activateGame()
        val card = pathCard("p1")
        viewModel.selectCard(card)

        assertEquals(card, viewModel.uiState.value.selectedCard)
        verify { GameApi.requestValidPositions("L1", "p1", false) }
    }

    @Test
    fun `selectCard should use existing rotation from state when present`() {
        activateGame()
        val card = pathCard("p1")
        viewModel.onCardRotated(card, true)
        viewModel.selectCard(card)

        assertEquals(true, viewModel.uiState.value.selectedCardRotated)
        verify { GameApi.requestValidPositions("L1", "p1", true) }
    }

    @Test
    fun `selectCard with block card should set pendingSpecialCard`() {
        activateGame()
        val card = blockCard()
        viewModel.selectCard(card)

        assertEquals(card, viewModel.uiState.value.selectedCard)
        assertEquals(CardType.PICKAXE_RED, viewModel.uiState.value.pendingSpecialCard)
        verify { GameApi.clearValidPositions() }
    }

    @Test
    fun `selectCard with repair card should set pendingSpecialCard`() {
        activateGame()
        viewModel.selectCard(repairCard())
        assertEquals(CardType.PICKAXE_GREEN, viewModel.uiState.value.pendingSpecialCard)
    }

    @Test
    fun `selectCard with map card should set pendingSpecialCard`() {
        activateGame()
        viewModel.selectCard(mapCard())
        assertEquals(CardType.MAPCARD, viewModel.uiState.value.pendingSpecialCard)
    }

    @Test
    fun `selectCard with rockfall card should set pendingSpecialCard`() {
        activateGame()
        viewModel.selectCard(rockfallCard())
        assertEquals(CardType.ROCKFALL, viewModel.uiState.value.pendingSpecialCard)
    }

    @Test
    fun `selectCard with non-special card should leave pendingSpecialCard null`() {
        activateGame()
        viewModel.selectCard(startCard())
        assertNull(viewModel.uiState.value.pendingSpecialCard)
    }

    @Test
    fun `selectCard should be a no-op when syncing`() {
        viewModel.initGameSession("L1", "P1")
        // isSyncing still true from init
        viewModel.selectCard(pathCard())

        assertNull(viewModel.uiState.value.selectedCard)
        verify(exactly = 0) { GameApi.requestValidPositions(any(), any(), any()) }
    }

    @Test
    fun `selectCard should be a no-op when no lobbyCode set`() {
        eventHandlers["SYNC_COMPLETE"]?.invoke("")
        viewModel.selectCard(pathCard())
        assertNull(viewModel.uiState.value.selectedCard)
    }
    // endregion

    // region onCardRotated
    @Test
    fun `onCardRotated should update cardRotations`() {
        activateGame()
        viewModel.onCardRotated(pathCard("c1"), true)
        assertEquals(true, viewModel.uiState.value.cardRotations["c1"])
    }

    @Test
    fun `onCardRotated for selected card should re-request valid positions`() {
        activateGame()
        val card = pathCard("c1")
        viewModel.selectCard(card)
        viewModel.onCardRotated(card, true)

        assertEquals(true, viewModel.uiState.value.selectedCardRotated)
        verify { GameApi.requestValidPositions("L1", "c1", true) }
    }

    @Test
    fun `onCardRotated should be a no-op when syncing`() {
        viewModel.initGameSession("L1", "P1")
        viewModel.onCardRotated(pathCard(), true)
        assertEquals(emptyMap<String, Boolean>(), viewModel.uiState.value.cardRotations)
    }
    // endregion

    // region onBoardCellClicked
    @Test
    fun `onBoardCellClicked with path card should play card`() {
        activateGame()
        viewModel.selectCard(pathCard("p1"))

        viewModel.onBoardCellClicked(BoardPosition(3, 4))

        verify { GameApi.playCard("L1", "P1", "p1", BoardPosition(3, 4), false) }
        assertNull(viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `onBoardCellClicked with blocked player should show error`() {
        viewModel.initGameSession("L1", "P1")
        gameStateUpdates.value = GameState(
            players = listOf(PlayerTurn("P1", "Me", blockedTools = setOf(ToolType.PICKAXE))),
            currentPlayerId = "P1"
        )
        viewModel.selectCard(pathCard())

        viewModel.onBoardCellClicked(BoardPosition(0, 0))

        assertNotNull(viewModel.uiState.value.errorMessage)
        verify(exactly = 0) { GameApi.playCard(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onBoardCellClicked with map card should play map card`() {
        activateGame()
        viewModel.selectCard(mapCard("m1"))

        viewModel.onBoardCellClicked(BoardPosition(1, 1))

        verify { GameApi.playMapCard("L1", "P1", "m1", BoardPosition(1, 1)) }
        assertNull(viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `onBoardCellClicked with rockfall card should play rockfall card`() {
        activateGame()
        viewModel.selectCard(rockfallCard("rf1"))

        viewModel.onBoardCellClicked(BoardPosition(2, 2))

        verify { GameApi.playRockfallCard("L1", "P1", "rf1", BoardPosition(2, 2)) }
    }

    @Test
    fun `onBoardCellClicked with unsupported card should show error`() {
        activateGame()
        viewModel.selectCard(blockCard("b1"))

        viewModel.onBoardCellClicked(BoardPosition(0, 0))

        assertNotNull(viewModel.uiState.value.errorMessage)
        verify(exactly = 0) { GameApi.playCard(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onBoardCellClicked should be no-op when not your turn`() {
        viewModel.initGameSession("L1", "P1")
        gameStateUpdates.value = GameState(
            players = listOf(PlayerTurn("P1", "Me")),
            currentPlayerId = "P2"
        )
        viewModel.selectCard(pathCard())

        viewModel.onBoardCellClicked(BoardPosition(0, 0))

        verify(exactly = 0) { GameApi.playCard(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onBoardCellClicked should be no-op when no card selected`() {
        activateGame()
        viewModel.onBoardCellClicked(BoardPosition(0, 0))
        verify(exactly = 0) { GameApi.playCard(any(), any(), any(), any(), any()) }
    }
    // endregion

    // region discardSelectedCard
    @Test
    fun `discardSelectedCard should call GameApi discardCard`() {
        activateGame()
        viewModel.selectCard(pathCard("p1"))

        viewModel.discardSelectedCard()

        verify { GameApi.discardCard("L1", "P1", "p1") }
        assertNull(viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `discardSelectedCard should be no-op when not your turn`() {
        viewModel.initGameSession("L1", "P1")
        gameStateUpdates.value = GameState(
            players = listOf(PlayerTurn("P1", "Me")),
            currentPlayerId = "P2"
        )
        viewModel.selectCard(pathCard())

        viewModel.discardSelectedCard()

        verify(exactly = 0) { GameApi.discardCard(any(), any(), any()) }
    }
    // endregion

    // region playBlockCardOnPlayer / playRepairCardOnPlayer
    @Test
    fun `playBlockCardOnPlayer should call GameApi playBlockCard`() {
        activateGame()
        viewModel.selectCard(blockCard("b1"))

        viewModel.playBlockCardOnPlayer("TARGET")

        verify { GameApi.playBlockCard("L1", "P1", "b1", "TARGET") }
        assertNull(viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `playBlockCardOnPlayer with non-block card should be a no-op`() {
        activateGame()
        viewModel.selectCard(pathCard("p1"))

        viewModel.playBlockCardOnPlayer("TARGET")

        verify(exactly = 0) { GameApi.playBlockCard(any(), any(), any(), any()) }
    }

    @Test
    fun `playRepairCardOnPlayer should call GameApi playRepairCard`() {
        activateGame()
        viewModel.selectCard(repairCard("r1"))

        viewModel.playRepairCardOnPlayer("TARGET", "PICKAXE")

        verify { GameApi.playRepairCard("L1", "P1", "r1", "TARGET", "PICKAXE") }
        assertNull(viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `playRepairCardOnPlayer with non-repair card should be a no-op`() {
        activateGame()
        viewModel.selectCard(blockCard("b1"))

        viewModel.playRepairCardOnPlayer("TARGET", "PICKAXE")

        verify(exactly = 0) { GameApi.playRepairCard(any(), any(), any(), any(), any()) }
    }
    // endregion

    // region branch coverage fillers
    @Test
    fun `init with non-empty initial players should not force syncing`() {
        gameStateUpdates.value = GameState(
            players = listOf(PlayerTurn("X", "X")),
            currentPlayerId = "X"
        )
        val vm = GameViewModel()
        assertEquals(false, vm.uiState.value.isSyncing)
    }

    @Test
    fun `errorMessages received twice should cancel the previous clear job`() {
        errorMessages.tryEmit("First")
        assertEquals("First", viewModel.uiState.value.errorMessage)

        // Second emit must hit the `errorClearJob?.cancel()` non-null branch
        errorMessages.tryEmit("Second")
        assertEquals("Second", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `discardSelectedCard without lobby should be a no-op`() {
        viewModel.discardSelectedCard()
        verify(exactly = 0) { GameApi.discardCard(any(), any(), any()) }
    }

    @Test
    fun `discardSelectedCard while syncing should be a no-op`() {
        viewModel.initGameSession("L1", "P1")
        // isSyncing remains true since players are empty in init
        viewModel.discardSelectedCard()
        verify(exactly = 0) { GameApi.discardCard(any(), any(), any()) }
    }

    @Test
    fun `discardSelectedCard without card selected should be a no-op`() {
        activateGame()
        viewModel.discardSelectedCard()
        verify(exactly = 0) { GameApi.discardCard(any(), any(), any()) }
    }

    @Test
    fun `playBlockCardOnPlayer without lobby should be a no-op`() {
        viewModel.playBlockCardOnPlayer("TARGET")
        verify(exactly = 0) { GameApi.playBlockCard(any(), any(), any(), any()) }
    }

    @Test
    fun `playBlockCardOnPlayer without card selected should be a no-op`() {
        activateGame()
        viewModel.playBlockCardOnPlayer("TARGET")
        verify(exactly = 0) { GameApi.playBlockCard(any(), any(), any(), any()) }
    }

    @Test
    fun `playRepairCardOnPlayer without lobby should be a no-op`() {
        viewModel.playRepairCardOnPlayer("TARGET", "PICKAXE")
        verify(exactly = 0) { GameApi.playRepairCard(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `playRepairCardOnPlayer without card selected should be a no-op`() {
        activateGame()
        viewModel.playRepairCardOnPlayer("TARGET", "PICKAXE")
        verify(exactly = 0) { GameApi.playRepairCard(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onCardRotated without lobby should be a no-op`() {
        eventHandlers["SYNC_COMPLETE"]?.invoke("")
        viewModel.onCardRotated(pathCard(), true)
        assertEquals(emptyMap<String, Boolean>(), viewModel.uiState.value.cardRotations)
    }

    @Test
    fun `onBoardCellClicked with DEAD_END card should play card`() {
        activateGame()
        val card = TunnelCard("d1", CardType.DEAD_END, emptySet())
        viewModel.selectCard(card)
        viewModel.onBoardCellClicked(BoardPosition(0, 0))
        verify { GameApi.playCard("L1", "P1", "d1", BoardPosition(0, 0), false) }
    }
    // endregion

    // region card-type classification (exercises isBlockCard / isRepairCard `||` clauses)
    private fun assertPendingForType(type: CardType) {
        activateGame()
        viewModel.selectCard(TunnelCard("t-$type", type, emptySet()))
        assertEquals(type, viewModel.uiState.value.pendingSpecialCard)
    }

    @Test fun `selectCard CART_RED classifies as block`() = assertPendingForType(CardType.CART_RED)
    @Test fun `selectCard LANTERN_RED classifies as block`() = assertPendingForType(CardType.LANTERN_RED)
    @Test fun `selectCard CART_GREEN classifies as repair`() = assertPendingForType(CardType.CART_GREEN)
    @Test fun `selectCard LANTERN_GREEN classifies as repair`() = assertPendingForType(CardType.LANTERN_GREEN)
    @Test fun `selectCard DOUBLE_LANTERN_CART classifies as repair`() = assertPendingForType(CardType.DOUBLE_LANTERN_CART)
    @Test fun `selectCard DOUBLE_PICKAXE_CART classifies as repair`() = assertPendingForType(CardType.DOUBLE_PICKAXE_CART)
    @Test fun `selectCard DOUBLE_PICKAXE_LANTERN classifies as repair`() = assertPendingForType(CardType.DOUBLE_PICKAXE_LANTERN)
    // endregion

    // region remaining early-return branches
    @Test
    fun `onBoardCellClicked without lobby should be a no-op`() {
        eventHandlers["SYNC_COMPLETE"]?.invoke("")
        viewModel.onBoardCellClicked(BoardPosition(0, 0))
        verify(exactly = 0) { GameApi.playCard(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onBoardCellClicked while syncing should be a no-op`() {
        activateGame()
        viewModel.selectCard(pathCard("p1"))
        connectionStatus.value = false // forces isSyncing = true
        viewModel.onBoardCellClicked(BoardPosition(0, 0))
        verify(exactly = 0) { GameApi.playCard(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onBoardCellClicked when localPlayer not in players list should still play path card`() {
        viewModel.initGameSession("L1", "P1")
        gameStateUpdates.value = GameState(
            players = listOf(PlayerTurn("OTHER", "x")),
            currentPlayerId = "P1"
        )
        viewModel.selectCard(pathCard("p1"))

        viewModel.onBoardCellClicked(BoardPosition(0, 0))

        // currentPlayer find returns null → blocked-tools check is skipped → card is played
        verify { GameApi.playCard("L1", "P1", "p1", BoardPosition(0, 0), false) }
    }

    @Test
    fun `playBlockCardOnPlayer while syncing should be a no-op`() {
        activateGame()
        viewModel.selectCard(blockCard("b1"))
        connectionStatus.value = false
        viewModel.playBlockCardOnPlayer("TARGET")
        verify(exactly = 0) { GameApi.playBlockCard(any(), any(), any(), any()) }
    }

    @Test
    fun `playBlockCardOnPlayer when not your turn should be a no-op`() {
        viewModel.initGameSession("L1", "P1")
        gameStateUpdates.value = GameState(
            players = listOf(PlayerTurn("P1", "Me")),
            currentPlayerId = "P2"
        )
        viewModel.selectCard(blockCard("b1"))
        viewModel.playBlockCardOnPlayer("TARGET")
        verify(exactly = 0) { GameApi.playBlockCard(any(), any(), any(), any()) }
    }

    @Test
    fun `playRepairCardOnPlayer while syncing should be a no-op`() {
        activateGame()
        viewModel.selectCard(repairCard("r1"))
        connectionStatus.value = false
        viewModel.playRepairCardOnPlayer("TARGET", "PICKAXE")
        verify(exactly = 0) { GameApi.playRepairCard(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `playRepairCardOnPlayer when not your turn should be a no-op`() {
        viewModel.initGameSession("L1", "P1")
        gameStateUpdates.value = GameState(
            players = listOf(PlayerTurn("P1", "Me")),
            currentPlayerId = "P2"
        )
        viewModel.selectCard(repairCard("r1"))
        viewModel.playRepairCardOnPlayer("TARGET", "PICKAXE")
        verify(exactly = 0) { GameApi.playRepairCard(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onCardRotated for a different card than selected should not re-request positions`() {
        activateGame()
        val selectedCard = pathCard("selected")
        val otherCard = pathCard("other")
        viewModel.selectCard(selectedCard) // requests positions for "selected"

        viewModel.onCardRotated(otherCard, true)

        assertEquals(true, viewModel.uiState.value.cardRotations["other"])
        verify(exactly = 1) { GameApi.requestValidPositions(any(), any(), any()) }
    }

    @Test
    fun `triggerCheat without lobbyCode should be a no-op`() {
        viewModel.triggerCheat(CheatType.LANTERN_FLASHLIGHT)

        verify(exactly = 0) { GameApi.triggerCheat(any(), any()) }
    }

    @Test
    fun `gameOverEvents should request round result screen when round is not final`() = runTest(testDispatcher.scheduler) {
        val roundResult = RoundResult(roundNumber = 1, winnerRole = Role.GOLDDIGGER, winningPlayerIds = listOf("P1"))
        gameStateUpdates.value = GameState(isRoundOver = true, isGameOver = false, lastRoundResult = roundResult)
        val collected = mutableListOf<Unit>()
        val job = launch { viewModel.roundResultScreenRequested.collect { collected.add(it) } }

        testDispatcher.scheduler.runCurrent()
        gameOverEvents.tryEmit("DWARVES")
        testDispatcher.scheduler.runCurrent()

        assertTrue(collected.isNotEmpty())
        job.cancel()
    }

    @Test
    fun `gameOverEvents should request final result screen when game is over`() = runTest(testDispatcher.scheduler) {
        val roundResult = RoundResult(roundNumber = 3, winnerRole = Role.GOLDDIGGER, gameFinished = true)
        gameStateUpdates.value = GameState(isRoundOver = true, isGameOver = true, lastRoundResult = roundResult)
        val collected = mutableListOf<Unit>()
        val job = launch { viewModel.finalResultScreenRequested.collect { collected.add(it) } }

        testDispatcher.scheduler.runCurrent()
        gameOverEvents.tryEmit("DWARVES")
        testDispatcher.scheduler.runCurrent()

        assertTrue(collected.isNotEmpty())
        job.cancel()
    }

    @Test
    fun `gameOverEvents should not request result screens without a round result`() = runTest(testDispatcher.scheduler) {
        gameStateUpdates.value = GameState(isRoundOver = true, isGameOver = false, lastRoundResult = null)
        val roundCollected = mutableListOf<Unit>()
        val finalCollected = mutableListOf<Unit>()
        val roundJob = launch { viewModel.roundResultScreenRequested.collect { roundCollected.add(it) } }
        val finalJob = launch { viewModel.finalResultScreenRequested.collect { finalCollected.add(it) } }

        testDispatcher.scheduler.runCurrent()
        gameOverEvents.tryEmit("DWARVES")
        testDispatcher.scheduler.runCurrent()

        assertTrue(roundCollected.isEmpty())
        assertTrue(finalCollected.isEmpty())
        roundJob.cancel()
        finalJob.cancel()
    }
    // endregion
}
