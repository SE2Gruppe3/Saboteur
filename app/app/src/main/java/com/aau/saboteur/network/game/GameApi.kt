package com.aau.saboteur.network.game

import com.aau.saboteur.network.HttpClient
import com.aau.saboteur.network.NetworkConstants
import com.aau.shared.game.CreateGameRequest
import com.aau.shared.game.GameState
import com.aau.shared.game.Player
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

object GameApi {
    private val _gameStateUpdates = MutableSharedFlow<GameState>(replay = 1, extraBufferCapacity = 10)
    val gameStateUpdates: SharedFlow<GameState> = _gameStateUpdates.asSharedFlow()

    private val _errorMessages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 10)
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    private var webSocket: WebSocket? = null

    init {
        connectWebSocket()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun reset() {
        closeWebSocket()
        _gameStateUpdates.resetReplayCache()
    }

    fun connectWebSocket() {
        closeWebSocket()
        val request = Request.Builder()
            .url(NetworkConstants.gameWebSocketEndpoint)
            .build()
        
        webSocket = HttpClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val newState = text.toGameState()
                    _gameStateUpdates.tryEmit(newState)
                } catch (e: Exception) {
                    _errorMessages.tryEmit("Failed to parse game state: ${e.message}")
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorMsg = response?.let { "Connection failed: ${it.code}" } ?: "Connection failed: ${t.message}"
                _errorMessages.tryEmit(errorMsg)
                t.printStackTrace()
            }
        })
    }

    fun closeWebSocket() {
        webSocket?.close(1000, "App closed or Reconnecting")
        webSocket = null
    }

    fun startGame(players: List<Player>) {
        val request = CreateGameRequest(players = players)
        val message = JSONObject().apply {
            put("action", "START_GAME")
            put("data", JSONObject(request.toJson()))
        }.toString()
        
        val sent = webSocket?.send(message) ?: false
        if (!sent) {
            _errorMessages.tryEmit("Failed to send start game request. Connection might be down.")
        }
    }
}
