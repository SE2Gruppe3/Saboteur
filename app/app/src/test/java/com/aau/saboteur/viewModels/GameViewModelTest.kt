package com.aau.saboteur.viewModels

import android.os.Looper
import com.aau.saboteur.network.NetworkConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GameViewModelTest {

    private lateinit var mockWebServer: MockWebServer
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Update NetworkConstants.baseUrl for testing
        NetworkConstants.setBaseUrl("http://${mockWebServer.hostName}:${mockWebServer.port}")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        mockWebServer.shutdown()
        // Reset baseUrl to avoid affecting other tests
        NetworkConstants.setBaseUrl("")
    }

    @Test
    fun `startGame success updates uiState with gameState`() = runTest {
        val jsonResponse = """
            {
                "players": [
                    {"playerId": "1", "playerName": "Alice", "turnOrder": 1},
                    {"playerId": "2", "playerName": "Bob", "turnOrder": 2}
                ],
                "currentPlayerId": "1"
            }
        """.trimIndent()
        
        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val viewModel = GameViewModel()
        
        viewModel.startGame()
        
        // Initial state check
        advanceUntilIdle()
        assertTrue("Should be in starting state initially", viewModel.uiState.value.isStartingGame)
        
        // Wait for MockWebServer to receive the request (happens on IO thread)
        mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        
        // Now wait for the coroutine to resume on the Main dispatcher and update the state
        // We need to allow the background thread to finish and the Main dispatcher to pick it up.
        // Since we are using runTest, we might need to poll if the IO work is truly async.
        
        var attempts = 0
        while (viewModel.uiState.value.isStartingGame && attempts < 50) {
            // This helps Robolectric process any pending tasks on the main looper
            shadowOf(Looper.getMainLooper()).idle()
            // This helps the test dispatcher advance if there are pending coroutines
            advanceUntilIdle()
            if (viewModel.uiState.value.isStartingGame) {
                Thread.sleep(20)
            }
            attempts++
        }
        
        assertFalse("Should not be starting game anymore (attempts: $attempts)", viewModel.uiState.value.isStartingGame)
        assertNull("Should not have error message", viewModel.uiState.value.errorMessage)
        assertEquals(2, viewModel.uiState.value.gameState.players.size)
        assertEquals("1", viewModel.uiState.value.gameState.currentPlayerId)
    }

    @Test
    fun `startGame failure updates uiState with error message`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val viewModel = GameViewModel()
        
        viewModel.startGame()
        
        advanceUntilIdle()
        mockWebServer.takeRequest(5, TimeUnit.SECONDS)

        var attempts = 0
        while (viewModel.uiState.value.isStartingGame && attempts < 50) {
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()
            if (viewModel.uiState.value.isStartingGame) {
                Thread.sleep(20)
            }
            attempts++
        }
        
        assertFalse("Should not be starting game anymore", viewModel.uiState.value.isStartingGame)
        assertTrue("Error message should contain 500: ${viewModel.uiState.value.errorMessage}", 
            viewModel.uiState.value.errorMessage?.contains("500") == true)
    }
}
