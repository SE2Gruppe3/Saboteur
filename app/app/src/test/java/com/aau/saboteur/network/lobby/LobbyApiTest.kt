package com.aau.saboteur.network.lobby

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.network.HttpClient
import com.aau.saboteur.network.WebSocketManager
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.Runs
import io.mockk.just
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import okhttp3.Call
import okhttp3.Response
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class LobbyApiTest {

    private val eventHandlers = mutableMapOf<String, (String) -> Unit>()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // WebSocketManager ZUERST mocken ...
        mockkObject(WebSocketManager)
        every { WebSocketManager.onEvent(any(), any()) } answers {
            eventHandlers[firstArg()] = secondArg()
        }
        every { WebSocketManager.sendCommand(any(), any()) } just Runs
        every { WebSocketManager.connect(any(), any()) } just Runs

        // ... dann Handler neu registrieren lassen.
        // LobbyApi ist ein Kotlin-Singleton: init{} läuft nur einmal pro JVM-Lifetime,
        // also BEVOR mockkObject() aktiv ist. resetForTest() ruft registerEventHandlers()
        // erneut auf, diesmal gegen den aktiven Mock.
        LobbyApi.resetForTest()
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
        eventHandlers.clear()
    }

    @Test
    fun `kickPlayer sends LOBBY_KICK command with correct payload`() {
        val lobbyCode = "L123"
        val hostId = "H456"
        val targetId = "T789"

        LobbyApi.kickPlayer(lobbyCode, hostId, targetId)

        verify {
            WebSocketManager.sendCommand("LOBBY_KICK", match<JSONObject> { json ->
                json.getString("lobbyCode") == lobbyCode &&
                        json.getString("hostId") == hostId &&
                        json.getString("targetPlayerId") == targetId
            })
        }
    }

    @Test
    fun `createLobby handles network exception`() = runTest {
        mockkObject(HttpClient)
        val mockCall = mockk<Call>()
        every { HttpClient.okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } throws java.io.IOException("No internet")

        var error: String? = null
        val job = backgroundScope.launch(testDispatcher) {
            error = LobbyApi.errorMessages.first()
        }

        LobbyApi.createLobby("Tester")
        job.join()

        assertNotNull("Es sollte eine Fehlermeldung emittiert werden", error)
        assertTrue("Fehlermeldung sollte einen Inhalt besitzen", error!!.isNotBlank())
    }

    @Test
    fun `joinLobby handles non-successful response`() = runTest {
        mockkObject(HttpClient)
        val mockCall = mockk<Call>()
        val mockResponse = mockk<Response>(relaxed = true)
        every { HttpClient.okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns false
        every { mockResponse.code } returns 400

        var error: String? = null
        val job = backgroundScope.launch(testDispatcher) {
            error = LobbyApi.errorMessages.first()
        }

        LobbyApi.joinLobby("CODE", "Player")
        job.join()

        assertNotNull("Es sollte eine Fehlermeldung emittiert werden", error)
        assertTrue("Fehlermeldung sollte einen Inhalt besitzen", error!!.isNotBlank())
    }

    @Test
    fun `PLAYER_KICKED event updates playerKicked flow and clears lobby state`() = runTest {
        val handler = eventHandlers["PLAYER_KICKED"]
        assertNotNull("Handler für PLAYER_KICKED wurde nicht registriert", handler)

        var kickedId: String? = null
        var lastLobbyState: LobbyState? = mockk()

        val jobKicked = launch(testDispatcher) {
            LobbyApi.playerKicked.collect { kickedId = it }
        }
        val jobState = launch(testDispatcher) {
            LobbyApi.lobbyStateUpdates.collect { lastLobbyState = it }
        }

        handler!!.invoke("""{"playerId":"KICKED_ID"}""")

        advanceUntilIdle()

        jobKicked.cancel()
        jobState.cancel()

        assertEquals("KICKED_ID", kickedId)
        assertNull("Der Lobby-State hätte genullt werden müssen", lastLobbyState)
    }
}