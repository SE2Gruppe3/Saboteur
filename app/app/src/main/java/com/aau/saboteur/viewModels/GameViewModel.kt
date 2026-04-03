package com.aau.saboteur.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aau.saboteur.mockeddata.mockPlayers
import com.aau.saboteur.network.game.GameApi
import com.aau.shared.game.GameState
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

    fun startGame() {
        if (_uiState.value.isStartingGame) return

        _uiState.value = _uiState.value.copy(
            isStartingGame = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val result = GameApi.startGame(mockPlayers)
            _uiState.value = if (result.isSuccess) {
                _uiState.value.copy(
                    isStartingGame = false,
                    gameState = result.getOrNull() ?: GameState(players = emptyList(), currentPlayerId = null),
                    errorMessage = null
                )
            } else {
                _uiState.value.copy(
                    isStartingGame = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Could not start game."
                )
            }
        }
    }
}
