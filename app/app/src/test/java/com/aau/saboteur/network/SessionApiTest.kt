package com.aau.saboteur.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aau.saboteur.model.SessionInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [SessionApi] using Robolectric and MockWebServer.
 * 
 * Focuses on verifying:
 * 1. Correct URL configuration logic.
 * 2. Error handling (e.g., 404/500 responses).
 * 3. Successful session creation and local credential storage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Override base URL to point to mock server
        NetworkConstants.setBaseUrl(mockWebServer.url("/").toString().removeSuffix("/"))
        
        // Inject test dispatcher and reset flows
        SessionApi.setDispatcher(testDispatcher)
        SessionApi.reset()
        
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        NetworkConstants.setBaseUrl("") 
    }

    @Test
    fun `test URL configuration logic exists`() {
        val currentBaseUrl = NetworkConstants.baseUrl
        assertTrue("Base URL should start with http", currentBaseUrl.startsWith("http"))
        
        val wsUrl = NetworkConstants.wsBaseUrl
        assertTrue("WebSocket URL should start with ws", wsUrl.startsWith("ws"))
    }

    /**
     * Verifies that a 404 response during reconnect is correctly captured
     * and emitted through the errorMessages flow.
     */
    @Test
    fun `reconnect handles 404 error correctly`() = runTest(testDispatcher) {
        // Setup mock response
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        
        // Setup local storage to trigger reconnect logic
        val prefs = context.getSharedPreferences("saboteur_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_session_id", "SESS1")
            .putString("my_player_id", "PL1")
            .commit()

        // Use a background job to collect the first non-null error message
        var capturedError: String? = null
        val collectionJob = launch {
            capturedError = SessionApi.errorMessages.filterNotNull().first()
        }

        SessionApi.reconnect(context)

        // Ensure we wait for the emission
        collectionJob.join()
        
        assertTrue("Error message should contain 404 code. Got: $capturedError", capturedError?.contains("404") == true)
    }

    /**
     * Tests successful session creation:
     * 1. API parses response.
     * 2. IDs are saved to SharedPreferences for recovery.
     */
    @Test
    fun `createSession saves session info on success`() = runTest(testDispatcher) {
        val responseBody = """
            {
                "sessionId": "NEW123",
                "players": [{"id": "host-id", "name": "Alice"}],
                "isStarted": false
            }
        """.trimIndent()
        
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        var capturedSession: SessionInfo? = null
        val collectionJob = launch {
            capturedSession = SessionApi.sessionUpdates.first()
        }

        SessionApi.createSession("Alice", context)
        collectionJob.join()
        
        assertEquals("NEW123", capturedSession?.sessionId)
        
        // Verify persistent storage (Critical for Game State Recovery)
        val prefs = context.getSharedPreferences("saboteur_prefs", Context.MODE_PRIVATE)
        assertEquals("NEW123", prefs.getString("last_session_id", null))
        assertEquals("host-id", prefs.getString("my_player_id", null))
    }

    @Test
    fun `createSession emits error on failure`() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        var capturedError: String? = null
        val collectionJob = launch {
            capturedError = SessionApi.errorMessages.filterNotNull().first()
        }

        SessionApi.createSession("TestPlayer", context)
        collectionJob.join()
        
        assertTrue("Error should mention 500 status. Got: $capturedError", capturedError?.contains("500") == true)
    }
}
