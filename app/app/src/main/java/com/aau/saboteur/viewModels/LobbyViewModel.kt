package com.aau.saboteur.viewModels


import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Player(
    val id: String,
    val name: String,
    val isReady: Boolean = false,
    val isHost: Boolean = false
)

data class LobbyState(
    val lobbyCode: String = "",
    val hostName: String = "",
    val players: List<Player> = emptyList(),
    val maxPlayers: Int = 10,
    val gameStarted: Boolean = false,
    val minPlayersToStart: Int = 3
)

class LobbyViewModel : ViewModel() {
    private val _lobbyState = MutableStateFlow(LobbyState())
    val lobbyState: StateFlow<LobbyState> = _lobbyState

    fun createLobby(playerName: String) {
        val newCode = generateLobbyCode()
        val hostPlayer = Player(
            id = "player_1",
            name = playerName,
            isReady = false,
            isHost = true
        )
        _lobbyState.value = LobbyState(
            lobbyCode = newCode,
            hostName = playerName,
            players = listOf(hostPlayer),
            maxPlayers = 5
        )
    }

    fun joinLobby(lobbyCode: String, playerName: String) {
        val newPlayer = Player(
            id = "player_${_lobbyState.value.players.size + 1}",
            name = playerName,
            isReady = false,
            isHost = false
        )
        _lobbyState.value = _lobbyState.value.copy(
            lobbyCode = lobbyCode,
            players = _lobbyState.value.players + newPlayer
        )
    }

    fun togglePlayerReady(playerId: String) {
        val updatedPlayers = _lobbyState.value.players.map { player ->
            if (player.id == playerId) {
                player.copy(isReady = !player.isReady)
            } else {
                player
            }
        }
        _lobbyState.value = _lobbyState.value.copy(players = updatedPlayers)
    }

    fun startGame(): Boolean {
        val currentState = _lobbyState.value
        val allReady = currentState.players.all { it.isReady }
        val enoughPlayers = currentState.players.size >= currentState.minPlayersToStart

        return if (allReady && enoughPlayers) {
            _lobbyState.value = currentState.copy(gameStarted = true)
            true
        } else {
            false
        }
    }

    fun removePlayer(playerId: String) {
        val updatedPlayers = _lobbyState.value.players.filter { it.id != playerId }
        _lobbyState.value = _lobbyState.value.copy(players = updatedPlayers)
    }

    private fun generateLobbyCode(): String {
        return (1000..9999).random().toString()
    }
}