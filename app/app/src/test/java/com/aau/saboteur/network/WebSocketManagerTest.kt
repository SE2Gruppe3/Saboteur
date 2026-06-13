package com.aau.saboteur.network

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketManagerTest {

    private lateinit var mockWebSocket: WebSocket
    private lateinit var mockOkHttpClient: OkHttpClient
    private val listenerSlot = slot<WebSocketListener>()

    @Before
    fun setup() {
        mockkObject(HttpClient)
        mockkObject(NetworkConstants)
        mockkStatic(Log::class)
        
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockWebSocket = mockk(relaxed = true)
        mockOkHttpClient = mockk(relaxed = true)

        every { NetworkConstants.mainWebSocketEndpoint } returns "ws://test"
        every { HttpClient.okHttpClient } returns mockOkHttpClient
        every { mockOkHttpClient.newWebSocket(any<Request>(), capture(listenerSlot)) } returns mockWebSocket

        resetWebSocketManager()
        
        // Clear handlers
        val eventHandlersField = WebSocketManager::class.java.getDeclaredField("eventHandlers")
        eventHandlersField.isAccessible = true
        (eventHandlersField.get(WebSocketManager) as MutableMap<*, *>).clear()
    }

    private fun resetWebSocketManager() {
        WebSocketManager.reset()
        val fields = listOf("webSocket", "isConnecting", "reconnectDelay")
        fields.forEach { fieldName ->
            val field = WebSocketManager::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            when (field.type) {
                Boolean::class.javaPrimitiveType -> field.set(WebSocketManager, false)
                Long::class.javaPrimitiveType -> field.set(WebSocketManager, 2000L)
                else -> field.set(WebSocketManager, null)
            }
        }
    }

    @After
    fun tearDown() {
        WebSocketManager.disconnect()
        unmockkObject(HttpClient)
        unmockkObject(NetworkConstants)
        unmockkStatic(Log::class)
    }

    @Test
    fun `connect starts websocket connection`() {
        WebSocketManager.connect()
        verify { mockOkHttpClient.newWebSocket(any<Request>(), any<WebSocketListener>()) }
    }

    @Test
    fun `connect does nothing if already connecting`() {
        WebSocketManager.connect()
        WebSocketManager.connect()
        verify(exactly = 1) { mockOkHttpClient.newWebSocket(any<Request>(), any<WebSocketListener>()) }
    }

    @Test
    fun `connect registers if already connected and params provided`() {
        WebSocketManager.connect("P1", "L1")
        val listener = listenerSlot.captured
        listener.onOpen(mockWebSocket, mockk<Response>(relaxed = true))
        
        WebSocketManager.connect("P1", "L1")
        
        verify {
            mockWebSocket.send(match<String> { it.contains("REGISTER") })
        }
    }

    @Test
    fun `onOpen updates connection status and schedules heartbeat`() = runTest {
        WebSocketManager.connect("P1", "L1")
        val listener = listenerSlot.captured
        listener.onOpen(mockWebSocket, mockk<Response>(relaxed = true))

        assertTrue(WebSocketManager.connectionStatus.first())
        
        // Trigger heartbeat runnable
        ShadowLooper.runMainLooperOneTask() 
        verify {
            mockWebSocket.send(match<String> { it.contains("HEARTBEAT") })
        }
    }

    @Test
    fun `onMessage triggers registered handlers`() {
        var receivedData = ""
        WebSocketManager.onEvent("TEST_EVENT") { data ->
            receivedData = data
        }

        WebSocketManager.connect()
        val listener = listenerSlot.captured
        
        val json = JSONObject().apply {
            put("type", "TEST_EVENT")
            put("data", "hello")
        }
        
        listener.onMessage(mockWebSocket, json.toString())
        assertEquals("hello", receivedData)
    }

    @Test
    fun `onMessage handles state flags correctly`() {
        WebSocketManager.connect("P1", "L1")
        val listener = listenerSlot.captured
        listener.onOpen(mockWebSocket, mockk<Response>(relaxed = true))
        
        assertTrue(WebSocketManager.isReconnecting)

        // SYNC_COMPLETE should clear isReconnecting
        listener.onMessage(mockWebSocket, "{\"type\":\"SYNC_COMPLETE\",\"data\":{}}")
        assertFalse(WebSocketManager.isReconnecting)

        // Set it back via reflection for another test case
        val isReconnectingField = WebSocketManager::class.java.getDeclaredField("isReconnecting")
        isReconnectingField.isAccessible = true
        isReconnectingField.set(WebSocketManager, true)

        listener.onMessage(mockWebSocket, "{\"type\":\"RECONNECT_SNAPSHOT\",\"data\":{}}")
        assertFalse(WebSocketManager.isReconnecting)
    }

    @Test
    fun `onMessage ignores malformed json`() {
        WebSocketManager.connect()
        val listener = listenerSlot.captured
        // Should not throw
        listener.onMessage(mockWebSocket, "not json")
        verify { Log.e(any<String>(), any<String>(), any<Exception>()) }
    }

    @Test
    fun `sendCommand sends json through websocket`() {
        WebSocketManager.connect()
        val listener = listenerSlot.captured
        listener.onOpen(mockWebSocket, mockk<Response>(relaxed = true))

        WebSocketManager.sendCommand("CHAT", "Hi")
        
        verify {
            mockWebSocket.send(match<String> {
                val json = JSONObject(it)
                json.getString("type") == "CHAT" && json.getString("data") == "Hi"
            })
        }
    }

    @Test
    fun `onFailure triggers reconnect with exponential backoff`() {
        WebSocketManager.connect()
        val listener = listenerSlot.captured
        
        // 1st failure -> 2000ms
        listener.onFailure(mockWebSocket, RuntimeException("Error"), null)
        ShadowLooper.runMainLooperOneTask() 
        ShadowLooper.idleMainLooper(2000, TimeUnit.MILLISECONDS)
        verify(exactly = 2) { mockOkHttpClient.newWebSocket(any<Request>(), any<WebSocketListener>()) }

        // 2nd failure -> 4000ms
        listener.onFailure(mockWebSocket, RuntimeException("Error"), null)
        ShadowLooper.runMainLooperOneTask()
        ShadowLooper.idleMainLooper(4000, TimeUnit.MILLISECONDS)
        verify(exactly = 3) { mockOkHttpClient.newWebSocket(any<Request>(), any<WebSocketListener>()) }
    }

    @Test
    fun `disconnect closes websocket and cancels heartbeat`() {
        WebSocketManager.connect()
        val listener = listenerSlot.captured
        listener.onOpen(mockWebSocket, mockk<Response>(relaxed = true))

        WebSocketManager.disconnect()

        verify { mockWebSocket.close(1000, "Normal closure") }

        // --- NEU: Den Handler explizit leeren, bevor wir idle gehen ---
        // Das stellt sicher, dass der Looper wirklich leer ist
        ShadowLooper.runMainLooperToNextTask()

        // Prüfen, ob Heartbeat gesendet wurde (darf nicht)
        // Statt 1 Stunde, reicht ein kurzes Idle nach dem expliziten remove
        verify(exactly = 0) { mockWebSocket.send(match<String> { it.contains("HEARTBEAT") }) }
    }
    @Test
    fun `register does nothing if playerId or lobbyCode is missing`() {
        WebSocketManager.reset()
        WebSocketManager.register()
        verify(exactly = 0) { mockWebSocket.send(any<String>()) }
    }

    @Test
    fun `onClosing triggers reconnect`() {
        WebSocketManager.connect()
        val listener = listenerSlot.captured
        
        listener.onClosing(mockWebSocket, 1000, "Bye")
        ShadowLooper.runMainLooperOneTask()
        ShadowLooper.idleMainLooper(2000, TimeUnit.MILLISECONDS)
        verify(exactly = 2) { mockOkHttpClient.newWebSocket(any<Request>(), any<WebSocketListener>()) }
    }
}
