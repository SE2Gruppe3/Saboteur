package com.aau.saboteur.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.network.WebSocketManager
import com.aau.saboteur.network.game.GameApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameUiState(
    val isSyncing: Boolean = false,
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
        observeSyncStatus()
        observeGameStateUpdates()
        observePlayerUpdates()
        observeCardsDealt()
        observeErrors()
        observeGameOverEvents()
        observeValidPositions()
        
        // Initialer Sync-Status
        if (_uiState.value.gameState.players.isEmpty()) {
            _uiState.update { it.copy(isSyncing = true) }
        }
    }

    private fun observeSyncStatus() {
        WebSocketManager.onEvent("SYNC_COMPLETE") {
            _uiState.update { it.copy(isSyncing = false) }
        }
        
        viewModelScope.launch {
            WebSocketManager.connectionStatus.collect { isConnected ->
                if (!isConnected) {
                    _uiState.update { it.copy(isSyncing = true) }
                }
            }
        }
    }

    private fun observeGameStateUpdates() {
        viewModelScope.launch {
            GameApi.gameStateUpdates.collect { newState ->
                _uiState.update { it.copy(
                    gameState = newState,
                    isStartingGame = false,
                    isSyncing = false, 
                    errorMessage = null,
                    selectedCard = null,
                    selectedCardRotated = false
                ) }
                _validPositions.value = emptyList()
            }
        }
    }

    private fun observePlayerUpdates() {
        viewModelScope.launch {
            GameApi.playerUpdates.collect { updatedPlayer ->
                _uiState.update { it.copy(player = updatedPlayer) }
            }
        }
    }

    private fun observeCardsDealt() {
        viewModelScope.launch {
            GameApi.cardsDealtUpdates.collect { hands ->
                _uiState.update { it.copy(hands = hands, cardRotations = emptyMap()) }
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
        _uiState.update { it.copy(isStartingGame = false, errorMessage = message) }
        errorClearJob = viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

    fun setLocalPlayerId(playerId: String?) {
        _uiState.update { it.copy(localPlayerId = playerId) }
    }

    fun selectCard(card: TunnelCard) {
        if (_uiState.value.isSyncing) return
        val current = _uiState.value.selectedCard
        if (current?.id == card.id) {
            _uiState.update { it.copy(selectedCard = null, selectedCardRotated = false) }
            if (card.type == CardType.PATH || card.type == CardType.DEAD_END) {
                GameApi.clearValidPositions()
            }
        } else {
            val isRotated = _uiState.value.cardRotations[card.id] ?: card.isRotated
            _uiState.update { it.copy(selectedCard = card, selectedCardRotated = isRotated) }
            if (card.type == CardType.PATH || card.type == CardType.DEAD_END) {
                GameApi.requestValidPositions(card.id, isRotated)
            } else {
                GameApi.clearValidPositions()
            }
        }
    }

    fun onCardRotated(card: TunnelCard, isRotated: Boolean) {
        if (_uiState.value.isSyncing) return
        val newRotations = _uiState.value.cardRotations + (card.id to isRotated)
        val newSelectedCardRotated = if (_uiState.value.selectedCard?.id == card.id) isRotated
                                     else _uiState.value.selectedCardRotated
        _uiState.update { it.copy(
            cardRotations = newRotations,
            selectedCardRotated = newSelectedCardRotated
        ) }
        if (_uiState.value.selectedCard?.id == card.id &&
            (card.type == CardType.PATH || card.type == CardType.DEAD_END)) {
            GameApi.requestValidPositions(card.id, isRotated)
        }
    }

    fun onBoardCellClicked(position: BoardPosition) {
        val state = _uiState.value
        if (state.isSyncing) return
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
        if (state.isSyncing) return
        val card = state.selectedCard ?: return
        val playerId = state.localPlayerId ?: return
        if (state.gameState.currentPlayerId != playerId) return

        GameApi.discardCard(playerId, card.id)
        _uiState.update { it.copy(selectedCard = null, selectedCardRotated = false) }
        GameApi.clearValidPositions()
    }
}
