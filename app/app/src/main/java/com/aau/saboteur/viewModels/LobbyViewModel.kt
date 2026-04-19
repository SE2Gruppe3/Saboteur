package com.aau.saboteur.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.network.lobby.LobbyApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LobbyViewModel : ViewModel() {

    private val _lobbyState = MutableStateFlow<LobbyState?>(null)
    val lobbyState: StateFlow<LobbyState?> = _lobbyState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            LobbyApi.lobbyStateUpdates.collect { state ->
                _lobbyState.value = state
            }
        }
        viewModelScope.launch {
            LobbyApi.errorMessages.collect { msg ->
                _errorMessage.value = msg
            }
        }
    }

    fun createLobby(playerName: String) {
        _errorMessage.value = null
        LobbyApi.createLobby(playerName)
    }

    fun joinLobby(lobbyCode: String, playerName: String) {
        _errorMessage.value = null
        LobbyApi.joinLobby(lobbyCode, playerName)
    }
}