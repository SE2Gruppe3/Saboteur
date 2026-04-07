package com.aau.saboteur.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aau.saboteur.mockeddata.mockPlayers
import com.aau.saboteur.network.game.GameApi
import com.aau.saboteur.model.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GameUiState(
    val isStartingGame: Boolean = false,
    val gameState: GameState = GameState(players = emptyList(), currentPlayerId = null),
    val errorMessage: String? = null
)

class GameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        observeGameStateUpdates()
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

    fun startGame() {
        if (_uiState.value.isStartingGame) return

        _uiState.value = _uiState.value.copy(
            isStartingGame = true,
            errorMessage = null
        )

        GameApi.startGame(mockPlayers)
    }
}
