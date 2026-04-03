package com.aau.saboteur.viewModels

import android.os.Looper
import com.aau.saboteur.network.NetworkConstants
import com.aau.saboteur.network.game.GameApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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

        GameApi.reset()

        val host = mockWebServer.hostName
        val port = mockWebServer.port
        NetworkConstants.setBaseUrl("http://$host:$port")

        GameApi.connectWebSocket()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        mockWebServer.shutdown()
        NetworkConstants.setBaseUrl("")
        GameApi.reset()
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
        
        // Mock the WebSocket handshake and response
        mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("START_GAME")) {
                    webSocket.send(jsonResponse)
                }
            }
        }))

        val viewModel = GameViewModel()
        
        viewModel.startGame()
        
        // Give it time to connect and send/receive
        advanceUntilIdle()
        
        var attempts = 0
        while (viewModel.uiState.value.isStartingGame && attempts < 50) {
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()
            if (viewModel.uiState.value.isStartingGame) {
                Thread.sleep(20)
            }
            attempts++
        }
        
        assertFalse("Should not be starting game anymore (attempts: $attempts)", viewModel.uiState.value.isStartingGame)
        assertNull("Should not have error message", viewModel.uiState.value.errorMessage)
        assertEquals("1", viewModel.uiState.value.gameState.currentPlayerId)
    }

    @Test
    fun `startGame failure updates uiState with error message`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val viewModel = GameViewModel()

        GameApi.connectWebSocket()
        
        viewModel.startGame()
        
        advanceUntilIdle()

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
        assertTrue("Error message should be present: ${viewModel.uiState.value.errorMessage}", 
            viewModel.uiState.value.errorMessage != null)
    }
}
