package com.aau.saboteur.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.network.game.GameApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GameUiState(
    val isStartingGame: Boolean = false,
    val gameState: GameState = GameState(players = emptyList(), currentPlayerId = null),
    val localPlayerId: String? = null,
    val player: Player? = null,
    val hands: Map<String, List<TunnelCard>>? = null,
    val errorMessage: String? = null
)

class GameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        observeGameStateUpdates()
        observePlayerUpdates()
        observeCardsDealt()
        observeErrors()
    }

    private fun observeGameStateUpdates() {
        viewModelScope.launch {
            GameApi.gameStateUpdates.collect { newState ->
                _uiState.value = _uiState.value.copy(
                    gameState = newState,
                    isStartingGame = false,
                    errorMessage = null
                )
            }
        }
    }

    private fun observePlayerUpdates() {
        viewModelScope.launch {
            GameApi.playerUpdates.collect { updatedPlayer ->
                _uiState.value = _uiState.value.copy(
                    player = updatedPlayer
                )
            }
        }
    }

    private fun observeCardsDealt() {
        viewModelScope.launch {
            GameApi.cardsDealtUpdates.collect { hands ->
                _uiState.value = _uiState.value.copy(hands = hands)
            }
        }
    }

    private fun observeErrors() {
        viewModelScope.launch {
            GameApi.errorMessages.collect { message ->
                _uiState.value = _uiState.value.copy(
                    isStartingGame = false,
                    errorMessage = message
                )
            }
        }
    }

    fun setLocalPlayerId(playerId: String?) {
        _uiState.value = _uiState.value.copy(localPlayerId = playerId)
    }
}
