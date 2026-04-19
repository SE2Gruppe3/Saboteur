package com.aau.saboteur.network.lobby

import com.aau.saboteur.model.LobbyCreateRequest
import com.aau.saboteur.model.LobbyJoinRequest
import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.network.HttpClient
import com.aau.saboteur.network.NetworkConstants
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

object LobbyApi {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _lobbyStateUpdates = MutableSharedFlow<LobbyState>(replay = 1, extraBufferCapacity = 10)
    val lobbyStateUpdates: SharedFlow<LobbyState> = _lobbyStateUpdates.asSharedFlow()

    private val _errorMessages = MutableSharedFlow<String?>(replay = 1, extraBufferCapacity = 10)
    val errorMessages: SharedFlow<String?> = _errorMessages.asSharedFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun createLobby(playerName: String) {
        scope.launch {
            try {
                val url = "${NetworkConstants.baseUrl}/api/lobby/create"
                val payload = json.encodeToString(LobbyCreateRequest(playerName))

                val req = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                HttpClient.okHttpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        _errorMessages.tryEmit("Create lobby failed: ${resp.code} $body")
                        return@use
                    }
                    _errorMessages.tryEmit(null)
                    _lobbyStateUpdates.tryEmit(json.decodeFromString(body))
                }
            } catch (e: Exception) {
                _errorMessages.tryEmit("Create lobby error: ${e.message}")
            }
        }
    }

    fun joinLobby(lobbyCode: String, playerName: String) {
        scope.launch {
            try {
                val url = "${NetworkConstants.baseUrl}/api/lobby/join"
                val payload = json.encodeToString(LobbyJoinRequest(lobbyCode, playerName))

                val req = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                HttpClient.okHttpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        _errorMessages.tryEmit("Join lobby failed: ${resp.code} $body")
                        return@use
                    }
                    _errorMessages.tryEmit(null)
                    _lobbyStateUpdates.tryEmit(json.decodeFromString(body))
                }
            } catch (e: Exception) {
                _errorMessages.tryEmit("Join lobby error: ${e.message}")
            }
        }
    }
}