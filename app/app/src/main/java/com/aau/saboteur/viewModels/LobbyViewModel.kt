package com.aau.saboteur.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.network.lobby.LobbyApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LobbyViewModel : ViewModel() {

    private val _lobbyState = MutableStateFlow<LobbyState?>(null)
    val lobbyState: StateFlow<LobbyState?> = _lobbyState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        // Lobby state updates vom Server
        viewModelScope.launch {
            LobbyApi.lobbyStateUpdates.collect { state ->
                _lobbyState.value = state
            }
        }

        // Errors vom WebSocketManager
        viewModelScope.launch {
            LobbyApi.errorMessages.collect { msg ->
                _errorMessage.value = msg
            }
        }
    }

    fun createLobby(playerName: String) {
        LobbyApi.createLobby(playerName)
    }

    fun joinLobby(lobbyCode: String, playerName: String) {
        LobbyApi.joinLobby(lobbyCode, playerName)
    }

    fun clearError() {
        _errorMessage.value = null
    }
}