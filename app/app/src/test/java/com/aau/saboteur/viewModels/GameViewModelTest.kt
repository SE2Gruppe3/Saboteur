package com.aau.saboteur.viewModels

import com.aau.saboteur.model.*
import com.aau.saboteur.network.game.GameApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
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

    private fun setupActiveSession() {
        viewModel.initGameSession("LOBBY123", "p1")
        // Inject a state with at least one player to clear isSyncing
        injectGameState(GameState(players = listOf(PlayerTurn("p1", "Alice", 1))))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun injectGameState(gameState: GameState) {
        val field = GameApi::class.java.getDeclaredField("_gameStateUpdates")
        field.isAccessible = true
        (field.get(GameApi) as MutableStateFlow<GameState>).value = gameState
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
    fun `selectCard updates selectedCard state`() {
        setupActiveSession()

        val card = TunnelCard(
            id = "path1",
            type = CardType.PATH,
            connections = setOf(Direction.LEFT)
        )

        viewModel.selectCard(card)
        assertEquals(card, viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `selectCard clears selectedCard when clicked again`() {
        setupActiveSession()

        val card = TunnelCard(
            id = "path1",
            type = CardType.PATH,
            connections = setOf(Direction.LEFT)
        )

        viewModel.selectCard(card)
        assertEquals(card, viewModel.uiState.value.selectedCard)

        viewModel.selectCard(card)
        assertNull(viewModel.uiState.value.selectedCard)
    }

    @Test
    fun `onCardRotated updates cardRotations map`() {
        setupActiveSession()

        val card = TunnelCard(
            id = "card1",
            type = CardType.PATH,
            connections = setOf(Direction.LEFT)
        )

        viewModel.onCardRotated(card, true)
        assertEquals(true, viewModel.uiState.value.cardRotations[card.id])
    }

    @Test
    fun `selectCard with BLOCK card sets pending card type`() {
        setupActiveSession()

        val blockCard = TunnelCard(
            id = "block1",
            type = CardType.CART_RED,
            connections = setOf(Direction.LEFT)
        )

        viewModel.selectCard(blockCard)
        assertEquals(CardType.CART_RED, viewModel.uiState.value.pendingSpecialCard)
    }

    @Test
    fun `selectCard with REPAIR card sets pending card type`() {
        setupActiveSession()

        val repairCard = TunnelCard(
            id = "repair1",
            type = CardType.CART_GREEN,
            connections = setOf(Direction.LEFT)
        )

        viewModel.selectCard(repairCard)
        assertEquals(CardType.CART_GREEN, viewModel.uiState.value.pendingSpecialCard)
    }

    @Test
    fun `selectCard with MAP card sets pending card type`() {
        setupActiveSession()

        val mapCard = TunnelCard(
            id = "map1",
            type = CardType.MAPCARD,
            connections = emptySet()
        )

        viewModel.selectCard(mapCard)
        assertEquals(CardType.MAPCARD, viewModel.uiState.value.pendingSpecialCard)
    }

    @Test
    fun `selectCard with ROCKFALL card sets pending card type`() {
        setupActiveSession()

        val rockfallCard = TunnelCard(
            id = "rockfall1",
            type = CardType.ROCKFALL,
            connections = emptySet()
        )

        viewModel.selectCard(rockfallCard)
        assertEquals(CardType.ROCKFALL, viewModel.uiState.value.pendingSpecialCard)
    }

    @Test
    fun `valid positions state is initialized empty`() {
        assertTrue(viewModel.validPositions.value.isEmpty())
    }
}
