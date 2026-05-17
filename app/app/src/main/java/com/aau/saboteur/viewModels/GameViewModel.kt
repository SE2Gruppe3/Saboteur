package com.aau.saboteur.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aau.saboteur.model.*
import com.aau.saboteur.network.WebSocketManager
import com.aau.saboteur.network.game.GameApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private fun isBlockCard(type: CardType) =
    type == CardType.CART_RED || type == CardType.LANTERN_RED || type == CardType.PICKAXE_RED

private fun isRepairCard(type: CardType) =
    type == CardType.CART_GREEN || type == CardType.LANTERN_GREEN || type == CardType.PICKAXE_GREEN
            || type == CardType.DOUBLE_LANTERN_CART || type == CardType.DOUBLE_PICKAXE_CART || type == CardType.DOUBLE_PICKAXE_LANTERN

private fun isMapCard(type: CardType) = type == CardType.MAPCARD
private fun isRockfallCard(type: CardType) = type == CardType.ROCKFALL

data class GameUiState(
    val isSyncing: Boolean = false,
    val isStartingGame: Boolean = false,
    val gameState: GameState = GameState(players = emptyList(), currentPlayerId = null),
    val lobbyCode: String? = null,
    val localPlayerId: String? = null,
    val player: Player? = null,
    val hands: Map<String, List<TunnelCard>>? = null,
    val errorMessage: String? = null,
    val selectedCard: TunnelCard? = null,
    val selectedCardRotated: Boolean = false,
    val cardRotations: Map<String, Boolean> = emptyMap(),
    val pendingSpecialCard: CardType? = null
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
                _uiState.update {
                    it.copy(
                        gameState = newState,
                        isStartingGame = false,
                        isSyncing = false,
                        errorMessage = null,
                        selectedCard = null,
                        selectedCardRotated = false,
                        pendingSpecialCard = null
                    )
                }
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

    fun initGameSession(lobbyCode: String, playerId: String) {
        _uiState.update { it.copy(lobbyCode = lobbyCode, localPlayerId = playerId) }
    }

    fun setLocalPlayerId(playerId: String?) {
        _uiState.update { it.copy(localPlayerId = playerId) }
    }

    fun selectCard(card: TunnelCard) {
        val state = _uiState.value
        if (state.isSyncing) return
        val lobbyCode = state.lobbyCode ?: return

        val current = state.selectedCard
        if (current?.id == card.id) {
            _uiState.update { it.copy(selectedCard = null, selectedCardRotated = false, pendingSpecialCard = null) }
            if (card.type == CardType.PATH || card.type == CardType.DEAD_END) {
                GameApi.clearValidPositions()
            }
        } else {
            val isRotated = state.cardRotations[card.id] ?: card.isRotated
            val pending = when {
                isBlockCard(card.type) -> card.type
                isRepairCard(card.type) -> card.type
                isMapCard(card.type) -> card.type
                isRockfallCard(card.type) -> card.type
                else -> null
            }
            _uiState.update { it.copy(selectedCard = card, selectedCardRotated = isRotated, pendingSpecialCard = pending) }
            if (card.type == CardType.PATH || card.type == CardType.DEAD_END) {
                GameApi.requestValidPositions(lobbyCode, card.id, isRotated)
            } else {
                GameApi.clearValidPositions()
            }
        }
    }

    fun onCardRotated(card: TunnelCard, isRotated: Boolean) {
        val state = _uiState.value
        if (state.isSyncing) return
        val lobbyCode = state.lobbyCode ?: return

        val newRotations = state.cardRotations + (card.id to isRotated)
        val newSelectedCardRotated = if (state.selectedCard?.id == card.id) isRotated
        else state.selectedCardRotated

        _uiState.update {
            it.copy(
                cardRotations = newRotations,
                selectedCardRotated = newSelectedCardRotated
            )
        }

        if (state.selectedCard?.id == card.id && (card.type == CardType.PATH || card.type == CardType.DEAD_END)) {
            GameApi.requestValidPositions(lobbyCode, card.id, isRotated)
        }
    }

    fun onBoardCellClicked(position: BoardPosition) {
        val state = _uiState.value
        val lobbyCode = state.lobbyCode ?: return
        if (state.isSyncing) return
        val card = state.selectedCard ?: return
        val playerId = state.localPlayerId ?: return
        if (state.gameState.currentPlayerId != playerId) return

        when {
            card.type == CardType.PATH || card.type == CardType.DEAD_END -> {
                GameApi.playCard(lobbyCode, playerId, card.id, position, state.selectedCardRotated)
                _uiState.update { it.copy(selectedCard = null, selectedCardRotated = false, pendingSpecialCard = null) }
            }
            isMapCard(card.type) -> {
                GameApi.playMapCard(lobbyCode, playerId, card.id, position)
                _uiState.update { it.copy(selectedCard = null, pendingSpecialCard = null) }
            }
            isRockfallCard(card.type) -> {
                GameApi.playRockfallCard(lobbyCode, playerId, card.id, position)
                _uiState.update { it.copy(selectedCard = null, pendingSpecialCard = null) }
            }
            else -> showError("Diese Karte kann hier nicht auf das Feld gespielt werden.")
        }
    }

    fun discardSelectedCard() {
        val state = _uiState.value
        val lobbyCode = state.lobbyCode ?: return
        if (state.isSyncing) return
        val card = state.selectedCard ?: return
        val playerId = state.localPlayerId ?: return
        if (state.gameState.currentPlayerId != playerId) return

        GameApi.discardCard(lobbyCode, playerId, card.id)
        _uiState.update { it.copy(selectedCard = null, selectedCardRotated = false, pendingSpecialCard = null) }
        GameApi.clearValidPositions()
    }

    fun playBlockCardOnPlayer(targetPlayerId: String) {
        val state = _uiState.value
        val lobbyCode = state.lobbyCode ?: return
        val card = state.selectedCard ?: return
        val playerId = state.localPlayerId ?: return
        if (state.isSyncing || state.gameState.currentPlayerId != playerId || !isBlockCard(card.type)) return

        GameApi.playBlockCard(lobbyCode, playerId, card.id, targetPlayerId)
        _uiState.update { it.copy(selectedCard = null, pendingSpecialCard = null) }
    }

    fun playRepairCardOnPlayer(targetPlayerId: String, tool: String) {
        val state = _uiState.value
        val lobbyCode = state.lobbyCode ?: return
        val card = state.selectedCard ?: return
        val playerId = state.localPlayerId ?: return
        if (state.isSyncing || state.gameState.currentPlayerId != playerId || !isRepairCard(card.type)) return

        GameApi.playRepairCard(lobbyCode, playerId, card.id, targetPlayerId, tool)
        _uiState.update { it.copy(selectedCard = null, pendingSpecialCard = null) }
    }
}