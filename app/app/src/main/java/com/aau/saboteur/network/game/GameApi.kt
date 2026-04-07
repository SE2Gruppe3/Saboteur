package com.aau.saboteur.network.game

import com.aau.saboteur.network.WebSocketManager
import com.aau.saboteur.model.CreateGameRequest
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

object GameApi {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _gameStateUpdates = MutableSharedFlow<GameState>(replay = 1, extraBufferCapacity = 10)
    val gameStateUpdates: SharedFlow<GameState> = _gameStateUpdates.asSharedFlow()

    val errorMessages: SharedFlow<String> = WebSocketManager.errorMessages

    init {
        observeWebSocketMessages()
    }

    private fun observeWebSocketMessages() {
        scope.launch {
            WebSocketManager.messages.collect { (type, data) ->
                when (type) {
                    "GAME_STATE_UPDATE" -> {
                        try {
                            val newState = data.toGameState()
                            _gameStateUpdates.tryEmit(newState)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun startGame(players: List<Player>) {
        val request = CreateGameRequest(players = players)
        val data = JSONObject(request.toJson())
        WebSocketManager.sendMessage("START_GAME", data)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun reset() {
        _gameStateUpdates.resetReplayCache()
    }
}
