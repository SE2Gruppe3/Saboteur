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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _gameOverEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val gameOverEvents: SharedFlow<String> = _gameOverEvents.asSharedFlow()

    private val _validPositions = MutableStateFlow<List<BoardPosition>>(emptyList())
    val validPositions: StateFlow<List<BoardPosition>> = _validPositions.asStateFlow()

    private var errorClearJob: Job? = null

    init {
        observeGameStateUpdates()
        observePlayerUpdates()
        observeCardsDealt()
        observeErrors()
        observeGameOverEvents()
        observeValidPositions()
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
                _validPositions.value = emptyList()
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

    private fun observeGameOverEvents() {
        viewModelScope.launch {
            GameApi.gameOverEvents.collect { winner ->
                _gameOverEvents.tryEmit(winner)
            }
        }
    }

    private fun observeValidPositions() {
        viewModelScope.launch {
            GameApi.validPositionsUpdates.collect { positions ->
                _validPositions.value = positions
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

    /**
     * Toggles selection of [card]. If [card] is already selected, deselects it and clears the
     * valid-positions highlight. Otherwise selects it and, for PATH/DEAD_END cards, requests a
     * fresh set of valid positions from the server.
     */
    fun selectCard(card: TunnelCard) {
        val current = _uiState.value.selectedCard
        if (current?.id == card.id) {
            _uiState.value = _uiState.value.copy(selectedCard = null, selectedCardRotated = false)
            if (card.type == CardType.PATH || card.type == CardType.DEAD_END) {
                GameApi.clearValidPositions()
            }
        } else {
            // Preserve any rotation the user already applied before selecting
            val isRotated = _uiState.value.cardRotations[card.id] ?: card.isRotated
            _uiState.value = _uiState.value.copy(selectedCard = card, selectedCardRotated = isRotated)
            if (card.type == CardType.PATH || card.type == CardType.DEAD_END) {
                GameApi.requestValidPositions(card.id, isRotated)
            } else {
                GameApi.clearValidPositions()
            }
        }
    }

    /**
     * Updates the rotation state for [card] and, if it is the currently selected PATH or
     * DEAD_END card, re-requests valid positions for the new orientation.
     */
    fun onCardRotated(card: TunnelCard, isRotated: Boolean) {
        val newRotations = _uiState.value.cardRotations + (card.id to isRotated)
        val newSelectedCardRotated = if (_uiState.value.selectedCard?.id == card.id) isRotated
                                     else _uiState.value.selectedCardRotated
        _uiState.value = _uiState.value.copy(
            cardRotations = newRotations,
            selectedCardRotated = newSelectedCardRotated
        )
        if (_uiState.value.selectedCard?.id == card.id &&
            (card.type == CardType.PATH || card.type == CardType.DEAD_END)) {
            GameApi.requestValidPositions(card.id, isRotated)
        }
    }

    /**
     * Handles a tap on a board cell. Places the currently selected card at [position] if it is
     * the local player's turn and a PATH or DEAD_END card is selected.
     */
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

    /**
     * Discards the currently selected card for the local player and clears valid positions.
     * No-op if no card is selected, no player ID is set, or it is not the local player's turn.
     */
    fun discardSelectedCard() {
        val state = _uiState.value
        val card = state.selectedCard ?: return
        val playerId = state.localPlayerId ?: return
        if (state.gameState.currentPlayerId != playerId) return

        GameApi.discardCard(playerId, card.id)
        _uiState.value = _uiState.value.copy(selectedCard = null, selectedCardRotated = false)
        GameApi.clearValidPositions()
    }
}
