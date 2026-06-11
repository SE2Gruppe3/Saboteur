package com.aau.saboteur.viewModels

import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Role
import com.aau.saboteur.model.RoundResult
import com.aau.saboteur.network.game.GameApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class GameViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: GameViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = GameViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        GameApi.reset()
    }

    @Test
    fun `initial state has no lobby or player set`() {
        val state = viewModel.uiState.value
        assertNull(state.localPlayerId)
        assertNull(state.lobbyCode)
        assertNull(state.errorMessage)
        assertNull(state.selectedCard)
    }

    @Test
    fun `initGameSession sets lobbyCode and localPlayerId`() {
        viewModel.initGameSession("LOBBY42", "player1")
        val state = viewModel.uiState.value
        assertEquals("LOBBY42", state.lobbyCode)
        assertEquals("player1", state.localPlayerId)
    }

    @Test
    fun `setLocalPlayerId updates localPlayerId in state`() {
        viewModel.setLocalPlayerId("player99")
        assertEquals("player99", viewModel.uiState.value.localPlayerId)
    }

    @Test
    fun `setLocalPlayerId with null clears localPlayerId`() {
        viewModel.setLocalPlayerId("player99")
        viewModel.setLocalPlayerId(null)
        assertNull(viewModel.uiState.value.localPlayerId)
    }

    @Test
    fun `setError stores message in state`() {
        viewModel.setError("something went wrong")
        assertEquals("something went wrong", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `dismissMapResult clears lastMapResult`() {
        viewModel.dismissMapResult()
        assertNull(viewModel.uiState.value.lastMapResult)
    }

    @Test
    fun `roundResultScreenRequested emits when round ends and game continues`() = runTest {
        val roundResult = RoundResult(
            roundNumber = 1,
            winnerRole = Role.GOLDDIGGER,
            winningPlayerIds = listOf("p1")
        )
        val collected = mutableListOf<Unit>()
        val job = launch { viewModel.roundResultScreenRequested.collect { collected.add(it) } }

        advanceUntilIdle()
        injectGameState(GameState(isRoundOver = true, isGameOver = false, lastRoundResult = roundResult))
        advanceUntilIdle()

        assertTrue("roundResultScreenRequested should have emitted", collected.isNotEmpty())
        job.cancel()
    }

    @Test
    fun `finalResultScreenRequested emits when game ends`() = runTest {
        val roundResult = RoundResult(
            roundNumber = 3,
            winnerRole = Role.SABOTEUR,
            winningPlayerIds = listOf("p2"),
            gameFinished = true
        )
        val collected = mutableListOf<Unit>()
        val job = launch { viewModel.finalResultScreenRequested.collect { collected.add(it) } }

        advanceUntilIdle()
        injectGameState(GameState(isRoundOver = true, isGameOver = true, lastRoundResult = roundResult))
        advanceUntilIdle()

        assertTrue("finalResultScreenRequested should have emitted", collected.isNotEmpty())
        job.cancel()
    }

    @Test
    fun `roundResultScreenRequested does not emit when round is not over`() = runTest {
        val collected = mutableListOf<Unit>()
        val job = launch { viewModel.roundResultScreenRequested.collect { collected.add(it) } }

        advanceUntilIdle()
        injectGameState(GameState(isRoundOver = false))
        advanceUntilIdle()

        assertTrue("roundResultScreenRequested should not emit mid-round", collected.isEmpty())
        job.cancel()
    }

    @Test
    fun `roundResultScreenRequested does not emit when lastRoundResult is null`() = runTest {
        val collected = mutableListOf<Unit>()
        val job = launch { viewModel.roundResultScreenRequested.collect { collected.add(it) } }

        advanceUntilIdle()
        injectGameState(GameState(isRoundOver = true, lastRoundResult = null))
        advanceUntilIdle()

        assertTrue("roundResultScreenRequested should not emit without a result", collected.isEmpty())
        job.cancel()
    }

    @Suppress("UNCHECKED_CAST")
    private fun injectGameState(gameState: GameState) {
        val field = GameApi::class.java.getDeclaredField("_gameStateUpdates")
        field.isAccessible = true
        (field.get(GameApi) as MutableStateFlow<GameState>).value = gameState
    }

    @Test
    fun `setLocalPlayerId updates state correctly`() {
        viewModel.setLocalPlayerId("player123")
        assertEquals("player123", viewModel.uiState.value.localPlayerId)
    }

    @Test
    fun `initGameSession sets both lobbyCode and playerId`() {
        viewModel.initGameSession("CODE456", "playerXYZ")
        val state = viewModel.uiState.value
        assertEquals("CODE456", state.lobbyCode)
        assertEquals("playerXYZ", state.localPlayerId)
    }

    @Test
    fun `setError stores error message in state`() {
        val errorMsg = "Connection failed"
        viewModel.setError(errorMsg)
        assertEquals(errorMsg, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `dismissMapResult clears lastMapResult from state`() {
        viewModel.dismissMapResult()
        assertNull(viewModel.uiState.value.lastMapResult)
    }

    @Test
    fun `initial state has no errors`() {
        val state = viewModel.uiState.value
        assertNull(state.errorMessage)
    }

    @Test
    fun `initial state has selectedCard as null`() {
        val state = viewModel.uiState.value
        assertNull(state.selectedCard)
    }
    @Test
    fun `selectCard with PATH card selects it`() = runTest {
        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(GameState(players = emptyList(), currentPlayerId = "p1"))

        val card = com.aau.saboteur.model.TunnelCard(
            id = "path1",
            type = com.aau.saboteur.model.CardType.PATH,
            connections = setOf(com.aau.saboteur.model.Direction.LEFT, com.aau.saboteur.model.Direction.RIGHT)
        )
        viewModel.selectCard(card)
        advanceUntilIdle()

        assertEquals(card, viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `selectCard deselects when same card clicked again`() = runTest {
        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(GameState(players = emptyList(), currentPlayerId = "p1"))

        val card = com.aau.saboteur.model.TunnelCard(
            id = "path1",
            type = com.aau.saboteur.model.CardType.PATH,
            connections = setOf(com.aau.saboteur.model.Direction.LEFT, com.aau.saboteur.model.Direction.RIGHT)
        )

        viewModel.selectCard(card)
        advanceUntilIdle()
        assertEquals(card, viewModel.uiState.value.selectedCard)

        viewModel.selectCard(card)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `selectCard with BLOCK card sets pending special card`() = runTest {
        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(GameState(players = emptyList(), currentPlayerId = "p1"))

        val blockCard = com.aau.saboteur.model.TunnelCard(
            id = "block1",
            type = com.aau.saboteur.model.CardType.CART_RED,
            connections = setOf(com.aau.saboteur.model.Direction.LEFT)
        )
        viewModel.selectCard(blockCard)
        advanceUntilIdle()

        assertEquals(com.aau.saboteur.model.CardType.CART_RED, viewModel.uiState.value.pendingSpecialCard)
    }

    @Test
    fun `selectCard with REPAIR card sets pending special card`() = runTest {
        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(GameState(players = emptyList(), currentPlayerId = "p1"))

        val repairCard = com.aau.saboteur.model.TunnelCard(
            id = "repair1",
            type = com.aau.saboteur.model.CardType.CART_GREEN,
            connections = setOf(com.aau.saboteur.model.Direction.LEFT)
        )
        viewModel.selectCard(repairCard)
        advanceUntilIdle()

        assertEquals(com.aau.saboteur.model.CardType.CART_GREEN, viewModel.uiState.value.pendingSpecialCard)
    }

    @Test
    fun `selectCard with MAP card sets pending special card`() = runTest {
        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(GameState(players = emptyList(), currentPlayerId = "p1"))

        val mapCard = com.aau.saboteur.model.TunnelCard(
            id = "map1",
            type = com.aau.saboteur.model.CardType.MAPCARD,
            connections = emptySet()
        )
        viewModel.selectCard(mapCard)
        advanceUntilIdle()

        assertEquals(com.aau.saboteur.model.CardType.MAPCARD, viewModel.uiState.value.pendingSpecialCard)
    }

    @Test
    fun `selectCard with ROCKFALL card sets pending special card`() = runTest {
        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(GameState(players = emptyList(), currentPlayerId = "p1"))

        val rockfallCard = com.aau.saboteur.model.TunnelCard(
            id = "rockfall1",
            type = com.aau.saboteur.model.CardType.ROCKFALL,
            connections = emptySet()
        )
        viewModel.selectCard(rockfallCard)
        advanceUntilIdle()

        assertEquals(com.aau.saboteur.model.CardType.ROCKFALL, viewModel.uiState.value.pendingSpecialCard)
    }

    @Test
    fun `onCardRotated updates rotation state`() = runTest {
        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(GameState(players = emptyList(), currentPlayerId = "p1"))

        val card = com.aau.saboteur.model.TunnelCard(
            id = "card1",
            type = com.aau.saboteur.model.CardType.PATH,
            connections = setOf(com.aau.saboteur.model.Direction.LEFT)
        )

        viewModel.onCardRotated(card, true)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.cardRotations[card.id])
    }

    @Test
    fun `onBoardCellClicked plays PATH card`() = runTest {
        val card = com.aau.saboteur.model.TunnelCard(
            id = "path1",
            type = com.aau.saboteur.model.CardType.PATH,
            connections = setOf(com.aau.saboteur.model.Direction.LEFT)
        )
        val gameState = GameState(
            players = listOf(com.aau.saboteur.model.PlayerTurn("p1", "Player1", 1)),
            currentPlayerId = "p1"
        )

        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(gameState)
        viewModel.selectCard(card)
        advanceUntilIdle()

        val position = com.aau.saboteur.model.BoardPosition(4, 5)
        viewModel.onBoardCellClicked(position)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `onBoardCellClicked with blocked player shows error`() = runTest {
        val card = com.aau.saboteur.model.TunnelCard(
            id = "path1",
            type = com.aau.saboteur.model.CardType.PATH,
            connections = setOf(com.aau.saboteur.model.Direction.LEFT)
        )
        val gameState = GameState(
            players = listOf(
                com.aau.saboteur.model.PlayerTurn(
                    "p1",
                    "Player1",
                    1,
                    blockedTools = setOf(com.aau.saboteur.model.ToolType.PICKAXE)
                )
            ),
            currentPlayerId = "p1"
        )

        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(gameState)
        viewModel.selectCard(card)
        advanceUntilIdle()

        val position = com.aau.saboteur.model.BoardPosition(4, 5)
        viewModel.onBoardCellClicked(position)
        advanceUntilIdle()

        assertEquals("Du bist blockiert und kannst keine Tunnel legen.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onBoardCellClicked with MAP card plays it`() = runTest {
        val mapCard = com.aau.saboteur.model.TunnelCard(
            id = "map1",
            type = com.aau.saboteur.model.CardType.MAPCARD,
            connections = emptySet()
        )
        val gameState = GameState(
            players = listOf(com.aau.saboteur.model.PlayerTurn("p1", "Player1", 1)),
            currentPlayerId = "p1"
        )

        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(gameState)
        viewModel.selectCard(mapCard)
        advanceUntilIdle()

        val position = com.aau.saboteur.model.BoardPosition(4, 5)
        viewModel.onBoardCellClicked(position)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `onBoardCellClicked with ROCKFALL card plays it`() = runTest {
        val rockfallCard = com.aau.saboteur.model.TunnelCard(
            id = "rockfall1",
            type = com.aau.saboteur.model.CardType.ROCKFALL,
            connections = emptySet()
        )
        val gameState = GameState(
            players = listOf(com.aau.saboteur.model.PlayerTurn("p1", "Player1", 1)),
            currentPlayerId = "p1"
        )

        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(gameState)
        viewModel.selectCard(rockfallCard)
        advanceUntilIdle()

        val position = com.aau.saboteur.model.BoardPosition(4, 5)
        viewModel.onBoardCellClicked(position)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `discardSelectedCard removes card`() = runTest {
        val card = com.aau.saboteur.model.TunnelCard(
            id = "path1",
            type = com.aau.saboteur.model.CardType.PATH,
            connections = setOf(com.aau.saboteur.model.Direction.LEFT)
        )
        val gameState = GameState(
            players = listOf(com.aau.saboteur.model.PlayerTurn("p1", "Player1", 1)),
            currentPlayerId = "p1"
        )

        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(gameState)
        viewModel.selectCard(card)
        advanceUntilIdle()

        viewModel.discardSelectedCard()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `playBlockCardOnPlayer plays block card`() = runTest {
        val blockCard = com.aau.saboteur.model.TunnelCard(
            id = "block1",
            type = com.aau.saboteur.model.CardType.CART_RED,
            connections = setOf(com.aau.saboteur.model.Direction.LEFT)
        )
        val gameState = GameState(
            players = listOf(
                com.aau.saboteur.model.PlayerTurn("p1", "Player1", 1),
                com.aau.saboteur.model.PlayerTurn("p2", "Player2", 2)
            ),
            currentPlayerId = "p1"
        )

        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(gameState)
        viewModel.selectCard(blockCard)
        advanceUntilIdle()

        viewModel.playBlockCardOnPlayer("p2")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `playRepairCardOnPlayer plays repair card`() = runTest {
        val repairCard = com.aau.saboteur.model.TunnelCard(
            id = "repair1",
            type = com.aau.saboteur.model.CardType.CART_GREEN,
            connections = setOf(com.aau.saboteur.model.Direction.LEFT)
        )
        val gameState = GameState(
            players = listOf(
                com.aau.saboteur.model.PlayerTurn("p1", "Player1", 1),
                com.aau.saboteur.model.PlayerTurn("p2", "Player2", 2)
            ),
            currentPlayerId = "p1"
        )

        viewModel.initGameSession("LOBBY", "p1")
        injectGameState(gameState)
        viewModel.selectCard(repairCard)
        advanceUntilIdle()

        viewModel.playRepairCardOnPlayer("p2", "PICKAXE")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedCard)
    }

}
