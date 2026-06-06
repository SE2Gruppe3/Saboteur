package com.aau.saboteur.viewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aau.saboteur.data.repository.SessionRepository
import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.LobbyVisibility
import com.aau.saboteur.network.WebSocketManager
import com.aau.saboteur.network.game.GameApi
import com.aau.saboteur.network.lobby.LobbyApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LobbyViewModel(
    application: Application,
    private val sessionRepository: SessionRepository = SessionRepository(application)
) : AndroidViewModel(application) {

    private val _lobbyState = MutableStateFlow<LobbyState?>(null)
    val lobbyState: StateFlow<LobbyState?> = _lobbyState.asStateFlow()

    private val _availableLobbies = MutableStateFlow<List<LobbyState>>(emptyList())
    val availableLobbies: StateFlow<List<LobbyState>> = _availableLobbies.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _playerId = MutableStateFlow<String?>(sessionRepository.getPlayerId())
    val playerId: StateFlow<String?> = _playerId.asStateFlow()

    private val _username = MutableStateFlow<String>(sessionRepository.getUserName() ?: "Gast")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var isInitialAutoReconnect = false

    init {
        observeLobbyUpdates()
        observeReconnectData()
        observeSyncStatus()
        observeConnectionForRefresh()
        
        // Auto-reconnect if session exists, otherwise just connect for lobby list
        attemptAutoReconnect()
    }

    private fun attemptAutoReconnect() {
        val pid = sessionRepository.getPlayerId()
        val code = sessionRepository.getLobbyCode()
        if (pid != null && code != null) {
            isInitialAutoReconnect = true
            _isSyncing.value = true
            LobbyApi.reconnect(pid, code)
        } else {
            WebSocketManager.connect()
        }
    }

    private fun observeConnectionForRefresh() {
        viewModelScope.launch {
            WebSocketManager.connectionStatus.collect { isConnected ->
                if (isConnected) {
                    refreshLobbies()
                }
            }
        }
    }

    private fun observeLobbyUpdates() {
        viewModelScope.launch {
            LobbyApi.lobbyStateUpdates.collect { state ->
                _lobbyState.value = state
                _isSyncing.value = false
                if (state == null) {
                    sessionRepository.clearLobby()
                } else {
                    isInitialAutoReconnect = false
                    val pid = _playerId.value
                    if (pid != null) {
                        sessionRepository.saveLobby(state.lobbyCode)
                    }
                }
            }
        }
        viewModelScope.launch {
            LobbyApi.allLobbies.collect { lobbies ->
                _availableLobbies.value = lobbies
            }
        }
        viewModelScope.launch {
            LobbyApi.errorMessages.collect { msg ->
                if (isInitialAutoReconnect) {
                    isInitialAutoReconnect = false
                    _isSyncing.value = false
                    return@collect
                }
                _errorMessage.value = msg
                _isSyncing.value = false
                // If reconnect fails with specific errors, clear session to avoid loops
                if (msg?.contains("403") == true || msg?.contains("404") == true) {
                    _lobbyState.value = null
                    sessionRepository.clearLobby()
                    WebSocketManager.reset()
                }
            }
        }
        viewModelScope.launch {
            LobbyApi.lobbyNotFound.collect { msg ->
                if (isInitialAutoReconnect) {
                    isInitialAutoReconnect = false
                    _isSyncing.value = false
                    _lobbyState.value = null
                    sessionRepository.clearLobby()
                    WebSocketManager.reset()
                    return@collect
                }
                _errorMessage.value = msg
                _isSyncing.value = false
                _lobbyState.value = null
                sessionRepository.clearLobby()
                WebSocketManager.reset()
            }
        }
    }

    private fun observeReconnectData() {
        viewModelScope.launch {
            LobbyApi.reconnectData.collect { data ->
                isInitialAutoReconnect = false
                _playerId.value = data.myPlayerId
                _lobbyState.value = data.lobbyState
                sessionRepository.saveSession(data.myPlayerId, data.lobbyState.lobbyCode, _username.value)
            }
        }
    }

    private fun observeSyncStatus() {
        WebSocketManager.onEvent("SYNC_COMPLETE") {
            _isSyncing.value = false
        }
        
        viewModelScope.launch {
            WebSocketManager.connectionStatus.collect { isConnected ->
                if (!isConnected) {
                    _isSyncing.value = true
                }
            }
        }
    }

    fun createLobby(playerName: String, visibility: LobbyVisibility = LobbyVisibility.PUBLIC) {
        val currentPid = _playerId.value 
        sessionRepository.clearLobby()
        
        WebSocketManager.disconnect()

        _errorMessage.value = null
        _isSyncing.value = true
        _username.value = playerName
        isInitialAutoReconnect = false
        LobbyApi.createLobby(playerName, currentPid, visibility)
    }

    fun joinLobby(lobbyCode: String, playerName: String) {
        val currentPid = _playerId.value
        sessionRepository.clearLobby()
        
        WebSocketManager.disconnect()

        _errorMessage.value = null
        _isSyncing.value = true
        _username.value = playerName
        isInitialAutoReconnect = false
        LobbyApi.joinLobby(lobbyCode, playerName, currentPid)
    }

    fun leaveLobby() {
        val currentState = _lobbyState.value ?: return
        val currentPlayerId = _playerId.value ?: return
        _errorMessage.value = null
        isInitialAutoReconnect = false
        LobbyApi.leaveLobby(currentState.lobbyCode, currentPlayerId)
        _lobbyState.value = null
        sessionRepository.clearLobby() 
        WebSocketManager.disconnect()
        WebSocketManager.reset()
        WebSocketManager.connect()
    }

    fun resetLobby() {
        _lobbyState.value = null
        sessionRepository.clearLobby()
        WebSocketManager.reset()
        GameApi.reset()
    }

    fun startGame() {
        val state = _lobbyState.value ?: return
        val players = state.players
        val lobbyCode = state.lobbyCode

        if (players.isEmpty()) return

        _errorMessage.value = null


        GameApi.startGame(lobbyCode, players)
    }

    fun refreshLobbies() {
        LobbyApi.fetchAllLobbies()
    }
    
    fun setUsername(name: String) {
        _username.value = name
    }
    
    fun saveIdentity(pid: String, name: String) {
        if (_playerId.value != pid) {
            sessionRepository.clearLobby()
        }
        _playerId.value = pid
        _username.value = name
        sessionRepository.saveIdentity(pid, name)
    }
}
