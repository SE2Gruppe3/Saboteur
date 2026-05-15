package com.aau.saboteur.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object WebSocketManager {
    private const val TAG = "WebSocketManager"
    
    private val _messages = MutableSharedFlow<Pair<String, String>>(replay = 1, extraBufferCapacity = 100)
    val messages: SharedFlow<Pair<String, String>> = _messages.asSharedFlow()

    private val _errorMessages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 10)
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    private val _connectionStatus = MutableSharedFlow<Boolean>(replay = 1)
    val connectionStatus: SharedFlow<Boolean> = _connectionStatus.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var isConnecting = false
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectDelay = 2000L

    private var savedPlayerId: String? = null
    private var savedLobbyCode: String? = null
    var isReconnecting = false
        private set

    private val eventHandlers = ConcurrentHashMap<String, MutableList<(String) -> Unit>>()

    // Heartbeat Task
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            val pid = savedPlayerId
            val code = savedLobbyCode
            if (webSocket != null && pid != null && code != null) {
                sendCommand("HEARTBEAT", JSONObject().apply {
                    put("playerId", pid)
                    put("lobbyCode", code)
                })
            }
            handler.postDelayed(this, 10000) // 10s Heartbeat
        }
    }

    fun connect(playerId: String? = null, lobbyCode: String? = null) {
        if (playerId != null) savedPlayerId = playerId
        if (lobbyCode != null) savedLobbyCode = lobbyCode

        if (webSocket != null) {
            if (playerId != null && lobbyCode != null) register(reconnect = true)
            return
        }

        val endpoint = NetworkConstants.mainWebSocketEndpoint
        if (isConnecting || endpoint.isBlank()) return

        isConnecting = true
        val request = Request.Builder().url(endpoint).build()

        webSocket = HttpClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnecting = false
                reconnectDelay = 2000L
                _connectionStatus.tryEmit(true)
                
                if (savedPlayerId != null && savedLobbyCode != null) {
                    isReconnecting = true
                    register(reconnect = true)
                }
                
                handler.post(heartbeatRunnable)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    val data = json.opt("data")?.toString() ?: ""
                    
                    if (type == "RECONNECT_SNAPSHOT" || type == "SYNC_COMPLETE" || type == "LOBBY_NOT_FOUND") {
                        isReconnecting = false
                    }

                    eventHandlers[type]?.forEach { it(data) }
                } catch (e: Exception) {
                    Log.e(TAG, "WS Parse Error", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnecting = false
                _connectionStatus.tryEmit(false)
                handler.removeCallbacks(heartbeatRunnable)
                handleReconnect()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionStatus.tryEmit(false)
                handler.removeCallbacks(heartbeatRunnable)
                handleReconnect()
            }
        })
    }

    private fun handleReconnect() {
        webSocket = null
        isConnecting = false
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ connect() }, reconnectDelay)
        reconnectDelay = (reconnectDelay * 2).coerceAtMost(30000L)
    }

    fun onEvent(type: String, handler: (String) -> Unit) {
        eventHandlers.getOrPut(type) { mutableListOf() }.add(handler)
    }

    fun sendCommand(type: String, data: Any) {
        val message = JSONObject().apply {
            put("type", type)
            put("data", data)
        }.toString()
        webSocket?.send(message)
    }

    fun register(reconnect: Boolean = false) {
        val pid = savedPlayerId ?: return
        val code = savedLobbyCode ?: return
        sendCommand("REGISTER", JSONObject().apply {
            put("playerId", pid)
            put("lobbyCode", code)
            put("reconnect", reconnect)
        })
    }
    
    fun disconnect() {
        handler.removeCallbacks(heartbeatRunnable)
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        reset()
    }

    fun reset() {
        savedPlayerId = null
        savedLobbyCode = null
        isReconnecting = false
    }
}
