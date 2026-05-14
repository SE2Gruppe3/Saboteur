package com.aau.saboteur.network.game

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CreateGameRequest
import com.aau.saboteur.model.DiscardCardRequest
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.PlayCardRequest
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.network.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

object GameApi {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _gameStateUpdates = MutableStateFlow(GameState(players = emptyList(), currentPlayerId = null))
    val gameStateUpdates: StateFlow<GameState> = _gameStateUpdates.asStateFlow()

    private val _playerUpdates = MutableStateFlow<Player?>(null)
    val playerUpdates: StateFlow<Player?> = _playerUpdates.asStateFlow()

    private val _cardsDealtUpdates = MutableStateFlow<Map<String, List<TunnelCard>>?>(null)
    val cardsDealtUpdates: StateFlow<Map<String, List<TunnelCard>>?> = _cardsDealtUpdates.asStateFlow()

    // SharedFlow(replay=0): new subscribers don't receive stale connection errors from before the game screen loaded
    private val _errorMessages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 10)
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    // SharedFlow(replay=0): navigation is one-shot, no subscriber should receive a stale winner
    private val _gameOverEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val gameOverEvents: SharedFlow<String> = _gameOverEvents.asSharedFlow()

    // SharedFlow(replay=1): new subscribers immediately receive the current valid positions set
    private val _validPositionsUpdates = MutableSharedFlow<List<BoardPosition>>(replay = 1, extraBufferCapacity = 1)
    val validPositionsUpdates: SharedFlow<List<BoardPosition>> = _validPositionsUpdates.asSharedFlow()

    init {
        observeWebSocketMessages()
        observeConnectionErrors()
    }

    private fun observeWebSocketMessages() {
        scope.launch {
            WebSocketManager.messages.collect { (type, data) ->
                when {
                    type == "GAME_STATE_UPDATE" -> {
                        runCatching { data.toGameState() }
                            .onSuccess { _gameStateUpdates.value = it }
                            .onFailure { it.printStackTrace() }
                    }
                    type.startsWith("PLAYER_DATA_") -> {
                        runCatching { data.toPlayer() }
                            .onSuccess { _playerUpdates.value = it }
                            .onFailure { it.printStackTrace() }
                    }
                    type == "CARDS_DEALT" -> {
                        runCatching { data.toHands() }
                            .onSuccess { _cardsDealtUpdates.value = it }
                            .onFailure { it.printStackTrace() }
                    }
                    type == "GAME_OVER" -> {
                        runCatching { data.toGameOverWinner() }
                            .onSuccess { _gameOverEvents.tryEmit(it) }
                            .onFailure { it.printStackTrace() }
                    }
                    type == "VALID_POSITIONS" -> {
                        runCatching { data.toValidPositions() }
                            .onSuccess { _validPositionsUpdates.tryEmit(it) }
                            .onFailure { it.printStackTrace() }
                    }
                    type == "ERROR" -> _errorMessages.tryEmit(data)
                }
            }
        }
    }

    private fun observeConnectionErrors() {
        scope.launch {
            WebSocketManager.errorMessages.collect { error ->
                _errorMessages.tryEmit(error)
            }
        }
    }

    fun startGame(players: List<Player>) {
        val request = CreateGameRequest(players = players)
        WebSocketManager.sendMessage("START_GAME", JSONObject(request.toJson()))
    }

    fun playCard(playerId: String, cardId: String, position: BoardPosition, isRotated: Boolean) {
        val request = PlayCardRequest(playerId, cardId, position, isRotated)
        WebSocketManager.sendMessage("PLAY_CARD", JSONObject(request.toJson()))
    }

    fun discardCard(playerId: String, cardId: String) {
        val request = DiscardCardRequest(playerId, cardId)
        WebSocketManager.sendMessage("DISCARD_CARD", JSONObject(request.toJson()))
    }

    /**
     * Asks the server to compute all valid board positions for [cardId].
     * Results are emitted via [validPositionsUpdates].
     *
     * @param cardId the ID of the card in the local player's hand
     * @param isRotated whether the card should be evaluated in its rotated (180°) orientation
     */
    fun requestValidPositions(cardId: String, isRotated: Boolean) {
        val payload = JSONObject().apply {
            put("cardId", cardId)
            put("isRotated", isRotated)
        }
        WebSocketManager.sendMessage("GET_VALID_POSITIONS", payload)
    }

    /**
     * Clears the current set of highlighted valid positions by emitting an empty list
     * to [validPositionsUpdates].
     */
    internal fun clearValidPositions() {
        _validPositionsUpdates.tryEmit(emptyList())
    }
}
