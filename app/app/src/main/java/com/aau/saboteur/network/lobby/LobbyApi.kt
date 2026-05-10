package com.aau.saboteur.network.lobby

import com.aau.saboteur.model.LobbyCreateRequest
import com.aau.saboteur.model.LobbyJoinRequest
import com.aau.saboteur.model.LobbyLeaveRequest
import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.network.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

object LobbyApi {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _lobbyStateUpdates = MutableSharedFlow<LobbyState?>(replay = 1, extraBufferCapacity = 10)
    val lobbyStateUpdates: SharedFlow<LobbyState?> = _lobbyStateUpdates.asSharedFlow()

    private val _allLobbies = MutableSharedFlow<List<LobbyState>>(replay = 1, extraBufferCapacity = 10)
    val allLobbies: SharedFlow<List<LobbyState>> = _allLobbies.asSharedFlow()

    private val _errorMessages = MutableSharedFlow<String?>(replay = 1, extraBufferCapacity = 10)
    val errorMessages: SharedFlow<String?> = _errorMessages.asSharedFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    init {
        scope.launch {
            WebSocketManager.messages.collect { (type, data) ->
                when (type) {
                    "LOBBY_STATE_UPDATE" -> {
                        try {
                            val state = json.decodeFromString<LobbyState>(data)
                            _lobbyStateUpdates.tryEmit(state)
                        } catch (e: Exception) {
                            _errorMessages.tryEmit("Failed to parse lobby state: ${e.message}")
                        }
                    }
                    "LOBBY_LEFT" -> {
                        _lobbyStateUpdates.tryEmit(null)
                    }
                    "LOBBY_LIST_UPDATE" -> {
                        try {
                            val list = json.decodeFromString<List<LobbyState>>(data)
                            _allLobbies.tryEmit(list)
                        } catch (e: Exception) {
                            _errorMessages.tryEmit("Failed to parse lobby list: ${e.message}")
                        }
                    }
                    "ERROR" -> {
                        _errorMessages.tryEmit(data)
                    }
                }
            }
        }
    }

    fun createLobby(playerName: String) {
        val request = LobbyCreateRequest(playerName)
        val jsonString = json.encodeToString(request)
        WebSocketManager.sendMessage("LOBBY_CREATE", JSONObject(jsonString))
    }

    fun joinLobby(lobbyCode: String, playerName: String) {
        val request = LobbyJoinRequest(lobbyCode, playerName)
        val jsonString = json.encodeToString(request)
        WebSocketManager.sendMessage("LOBBY_JOIN", JSONObject(jsonString))
    }

    fun leaveLobby(lobbyCode: String, playerId: String) {
        val request = LobbyLeaveRequest(lobbyCode, playerId)
        val jsonString = json.encodeToString(request)
        WebSocketManager.sendMessage("LOBBY_LEAVE", JSONObject(jsonString))
    }

    fun fetchAllLobbies() {
        // Now fetching via WebSocket instead of REST
        WebSocketManager.sendMessage("LOBBY_LIST_FETCH", "")
    }
}
