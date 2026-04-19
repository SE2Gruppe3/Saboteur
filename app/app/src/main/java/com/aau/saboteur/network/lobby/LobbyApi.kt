package com.aau.saboteur.network.lobby

import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.network.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

object LobbyApi {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _lobbyStateUpdates = MutableSharedFlow<LobbyState>(replay = 1, extraBufferCapacity = 10)
    val lobbyStateUpdates: SharedFlow<LobbyState> = _lobbyStateUpdates.asSharedFlow()

    val errorMessages: SharedFlow<String> = WebSocketManager.errorMessages

    init {
        observeWebSocketMessages()
    }

    private fun observeWebSocketMessages() {
        scope.launch {
            WebSocketManager.messages.collect { (type, data) ->
                when (type) {
                    "LOBBY_STATE_UPDATE" -> {
                        try {
                            val state = data.toLobbyState() // kommt als nächstes (LobbyJson)
                            _lobbyStateUpdates.tryEmit(state)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun createLobby(playerName: String) {
        val data = JSONObject().apply {
            put("playerName", playerName)
        }
        WebSocketManager.sendMessage("LOBBY_CREATE", data)
    }

    fun joinLobby(lobbyCode: String, playerName: String) {
        val data = JSONObject().apply {
            put("lobbyCode", lobbyCode)
            put("playerName", playerName)
        }
        WebSocketManager.sendMessage("LOBBY_JOIN", data)
    }
}