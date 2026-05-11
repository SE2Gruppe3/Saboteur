package com.aau.saboteur.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.network.game.GameApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val errorMessage: String? = null,
    val selectedCard: TunnelCard? = null,
    val selectedCardRotated: Boolean = false,
    val cardRotations: Map<String, Boolean> = emptyMap()
)

class GameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var errorClearJob: Job? = null

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
                    errorMessage = null,
                    selectedCard = null,
                    selectedCardRotated = false
                )
            }
        }
    }

    private fun observePlayerUpdates() {
        viewModelScope.launch {
            GameApi.playerUpdates.collect { updatedPlayer ->
                _uiState.value = _uiState.value.copy(player = updatedPlayer)
            }
        }
    }

    private fun observeCardsDealt() {
        viewModelScope.launch {
            GameApi.cardsDealtUpdates.collect { hands ->
                _uiState.value = _uiState.value.copy(hands = hands, cardRotations = emptyMap())
            }
        }
    }

    private fun observeErrors() {
        viewModelScope.launch {
            GameApi.errorMessages.collect { message ->
                showError(message)
            }
        }
    }

    private fun showError(message: String) {
        errorClearJob?.cancel()
        _uiState.value = _uiState.value.copy(isStartingGame = false, errorMessage = message)
        errorClearJob = viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }

    fun setLocalPlayerId(playerId: String?) {
        _uiState.value = _uiState.value.copy(localPlayerId = playerId)
    }

    fun selectCard(card: TunnelCard) {
        val current = _uiState.value.selectedCard
        if (current?.id == card.id) {
            _uiState.value = _uiState.value.copy(selectedCard = null, selectedCardRotated = false)
        } else {
            // Preserve any rotation the user already applied before selecting
            val isRotated = _uiState.value.cardRotations[card.id] ?: card.isRotated
            _uiState.value = _uiState.value.copy(selectedCard = card, selectedCardRotated = isRotated)
        }
    }

    fun onCardRotated(card: TunnelCard, isRotated: Boolean) {
        val newRotations = _uiState.value.cardRotations + (card.id to isRotated)
        val newSelectedCardRotated = if (_uiState.value.selectedCard?.id == card.id) isRotated
                                     else _uiState.value.selectedCardRotated
        _uiState.value = _uiState.value.copy(
            cardRotations = newRotations,
            selectedCardRotated = newSelectedCardRotated
        )
    }

    fun onBoardCellClicked(position: BoardPosition) {
        val state = _uiState.value
        val card = state.selectedCard ?: return
        val playerId = state.localPlayerId ?: return
        if (state.gameState.currentPlayerId != playerId) return

        if (card.type != CardType.PATH && card.type != CardType.DEAD_END) {
            showError("Diese Karte kann hier nicht platziert werden.")
            return
        }

        GameApi.playCard(playerId, card.id, position, state.selectedCardRotated)
    }

    fun discardSelectedCard() {
        val state = _uiState.value
        val card = state.selectedCard ?: return
        val playerId = state.localPlayerId ?: return
        if (state.gameState.currentPlayerId != playerId) return

        GameApi.discardCard(playerId, card.id)
        _uiState.value = _uiState.value.copy(selectedCard = null, selectedCardRotated = false)
    }
}