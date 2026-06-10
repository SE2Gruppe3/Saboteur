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
}
