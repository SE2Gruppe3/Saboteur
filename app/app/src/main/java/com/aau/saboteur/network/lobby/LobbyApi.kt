package com.aau.saboteur.network.lobby

import com.aau.saboteur.model.*
import com.aau.saboteur.network.HttpClient
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object LobbyApi {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _lobbyStateUpdates = MutableSharedFlow<LobbyState?>(replay = 1, extraBufferCapacity = 10)
    val lobbyStateUpdates: SharedFlow<LobbyState?> = _lobbyStateUpdates.asSharedFlow()

    private val _reconnectData = MutableSharedFlow<ReconnectResponse>(replay = 1)
    val reconnectData: SharedFlow<ReconnectResponse> = _reconnectData.asSharedFlow()

    private val _allLobbies = MutableSharedFlow<List<LobbyState>>(replay = 1, extraBufferCapacity = 10)
    val allLobbies: SharedFlow<List<LobbyState>> = _allLobbies.asSharedFlow()

    private val _errorMessages = MutableSharedFlow<String?>(replay = 1, extraBufferCapacity = 10)
    val errorMessages: SharedFlow<String?> = _errorMessages.asSharedFlow()

    private val _lobbyNotFound = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 10)
    val lobbyNotFound: SharedFlow<String> = _lobbyNotFound.asSharedFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    init {
        WebSocketManager.onEvent("LOBBY_STATE_UPDATE") { data ->
            try {
                val state = json.decodeFromString<LobbyState>(data)
                _lobbyStateUpdates.tryEmit(state)
            } catch (e: Exception) {
                _errorMessages.tryEmit("Fehler beim Verarbeiten des Lobby-Status")
            }
        }

        WebSocketManager.onEvent("LOBBY_LEFT") {
            _lobbyStateUpdates.tryEmit(null)
        }

        WebSocketManager.onEvent("LOBBY_LIST_UPDATE") { data ->
            try {
                val list = json.decodeFromString<List<LobbyState>>(data)
                _allLobbies.tryEmit(list)
            } catch (e: Exception) {}
        }

        WebSocketManager.onEvent("RECONNECT_SNAPSHOT") { data ->
            try {
                val snapshot = json.decodeFromString<ReconnectSnapshot>(data)
                _lobbyStateUpdates.tryEmit(snapshot.lobbyState)
            } catch (e: Exception) {}
        }

        WebSocketManager.onEvent("LOBBY_NOT_FOUND") { data ->
            val msg = try {
                val jsonBody = JSONObject(data)
                jsonBody.optString("payload", "Lobby auf dem Server nicht gefunden")
            } catch (e: Exception) {
                data.ifBlank { "Lobby auf dem Server nicht gefunden" }
            }
            _lobbyNotFound.tryEmit(msg)
            _lobbyStateUpdates.tryEmit(null)
        }

        WebSocketManager.onEvent("ERROR") { data ->
            _errorMessages.tryEmit(data)
        }
    }

    fun createLobby(playerName: String, playerId: String? = null) {
        scope.launch {
            try {
                val requestBody = json.encodeToString(LobbyCreateRequest(playerName, playerId)).toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("${HttpClient.baseUrl}/api/lobby/create")
                    .post(requestBody)
                    .build()

                HttpClient.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val data = json.decodeFromString<ReconnectResponse>(body)
                        _reconnectData.emit(data)
                        WebSocketManager.connect(data.myPlayerId, data.lobbyState.lobbyCode)
                    } else {
                         _errorMessages.emit("Lobby-Erstellung fehlgeschlagen (${response.code})")
                    }
                }
            } catch (e: Exception) {
                _errorMessages.emit("Netzwerkfehler")
            }
        }
    }

    fun joinLobby(lobbyCode: String, playerName: String, playerId: String? = null) {
        scope.launch {
            try {
                val requestBody = json.encodeToString(LobbyJoinRequest(lobbyCode, playerName, playerId)).toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("${HttpClient.baseUrl}/api/lobby/join")
                    .post(requestBody)
                    .build()

                HttpClient.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val data = json.decodeFromString<ReconnectResponse>(body)
                        _reconnectData.emit(data)
                        WebSocketManager.connect(data.myPlayerId, data.lobbyState.lobbyCode)
                    } else {
                        _errorMessages.emit("Beitritt fehlgeschlagen: Code ungültig oder Lobby voll")
                    }
                }
            } catch (e: Exception) {
                _errorMessages.emit("Netzwerkfehler beim Beitritt")
            }
        }
    }

    fun reconnect(playerId: String, lobbyCode: String) {
        scope.launch {
            try {
                val requestBody = json.encodeToString(ReconnectRequest(playerId, lobbyCode)).toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("${HttpClient.baseUrl}/api/lobby/reconnect")
                    .post(requestBody)
                    .build()

                HttpClient.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val data = json.decodeFromString<ReconnectResponse>(body)
                        _reconnectData.emit(data)
                        WebSocketManager.connect(playerId, lobbyCode)
                    } else {
                        if (response.code == 404) {
                            _lobbyNotFound.emit("Lobby nicht mehr aktiv")
                        } else {
                            _errorMessages.emit("Sitzung konnte nicht wiederhergestellt werden")
                        }
                    }
                }
            } catch (e: Exception) {
                _errorMessages.emit("Verbindung zum Server fehlgeschlagen")
            }
        }
    }

    fun leaveLobby(lobbyCode: String, playerId: String) {
        val request = LobbyLeaveRequest(lobbyCode, playerId)
        WebSocketManager.sendCommand("LOBBY_LEAVE", JSONObject(json.encodeToString(request)))
    }

    fun fetchAllLobbies() {
        WebSocketManager.sendCommand("LOBBY_LIST_FETCH", JSONObject())
    }
}
