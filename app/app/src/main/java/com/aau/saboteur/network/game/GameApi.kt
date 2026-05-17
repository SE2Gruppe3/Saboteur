package com.aau.saboteur.network.game

import com.aau.saboteur.model.*
import com.aau.saboteur.network.WebSocketManager
import com.aau.saboteur.network.lobby.LobbyApi
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

    private val _errorMessages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 10)
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    private val _gameOverEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val gameOverEvents: SharedFlow<String> = _gameOverEvents.asSharedFlow()

    private val _validPositionsUpdates = MutableSharedFlow<List<BoardPosition>>(replay = 1, extraBufferCapacity = 1)
    val validPositionsUpdates: SharedFlow<List<BoardPosition>> = _validPositionsUpdates.asSharedFlow()

    private val _reconnectSnapshotEvents = MutableSharedFlow<ReconnectSnapshot>(replay = 1)
    val reconnectSnapshotEvents: SharedFlow<ReconnectSnapshot> = _reconnectSnapshotEvents.asSharedFlow()

    init {
        observeEvents()
        observeConnectionErrors()
        observeLobbyReconnects()
    }

    private fun observeEvents() {
        WebSocketManager.onEvent("GAME_STATE_UPDATE") { data ->
            runCatching { data.toGameState() }
                .onSuccess { _gameStateUpdates.value = it }
                .onFailure { it.printStackTrace() }
        }

        WebSocketManager.onEvent("PLAYER_DATA") { data ->
            runCatching { data.toPlayer() }
                .onSuccess { player ->
                    _playerUpdates.value = player
                    // Sync hands if available in player data
                    if (player.hand.isNotEmpty()) {
                        val currentHands = _cardsDealtUpdates.value ?: emptyMap()
                        _cardsDealtUpdates.value = currentHands + (player.id to player.hand)
                    }
                }
                .onFailure { it.printStackTrace() }
        }

        WebSocketManager.onEvent("CARDS_DEALT") { data ->
            runCatching { data.toHands() }
                .onSuccess { _cardsDealtUpdates.value = it }
                .onFailure { it.printStackTrace() }
        }

        WebSocketManager.onEvent("GAME_OVER") { data ->
            runCatching { data.toGameOverWinner() }
                .onSuccess { _gameOverEvents.tryEmit(it) }
                .onFailure { it.printStackTrace() }
        }

        WebSocketManager.onEvent("VALID_POSITIONS") { data ->
            runCatching { data.toValidPositions() }
                .onSuccess { _validPositionsUpdates.tryEmit(it) }
                .onFailure { it.printStackTrace() }
        }

        WebSocketManager.onEvent("RECONNECT_SNAPSHOT") { data ->
            runCatching { data.toReconnectSnapshot() }
                .onSuccess { snapshot ->
                    _reconnectSnapshotEvents.tryEmit(snapshot)
                    _gameStateUpdates.value = snapshot.gameState ?: GameState()
                    _playerUpdates.value = snapshot.playerState
                    
                    val currentHands = _cardsDealtUpdates.value ?: emptyMap()
                    _cardsDealtUpdates.value = currentHands + (snapshot.playerState.id to snapshot.playerState.hand)
                    
                    WebSocketManager.sendCommand("SYNC_ACK", JSONObject())
                }
                .onFailure { it.printStackTrace() }
        }

        WebSocketManager.onEvent("ERROR") { data ->
            _errorMessages.tryEmit(data)
        }
    }

    private fun observeConnectionErrors() {
        scope.launch {
            WebSocketManager.errorMessages.collect { error ->
                _errorMessages.tryEmit(error)
            }
        }
    }

    private fun observeLobbyReconnects() {
        scope.launch {
            LobbyApi.reconnectData.collect { data ->
                val existingPlayer = data.lobbyState.players.find { it.id == data.myPlayerId }
                
                data.gameState?.let {
                    _gameStateUpdates.value = it
                }
                
                _playerUpdates.value = Player(
                    id = data.myPlayerId,
                    name = existingPlayer?.name ?: "",
                    hand = data.playerHand,
                    role = data.playerRole ?: existingPlayer?.role
                )
                
                // CRITICAL: Always update hands from HTTP response
                val currentHands = _cardsDealtUpdates.value ?: emptyMap()
                _cardsDealtUpdates.value = currentHands + (data.myPlayerId to data.playerHand)
            }
        }
    }

    fun startGame(players: List<Player>) {
        val request = CreateGameRequest(players = players)
        WebSocketManager.sendCommand("START_GAME", JSONObject(request.toJson()))
    }

    fun playCard(playerId: String, cardId: String, position: BoardPosition, isRotated: Boolean) {
        val request = PlayCardRequest(playerId, cardId, position, isRotated)
        WebSocketManager.sendCommand("PLAY_CARD", JSONObject(request.toJson()))
    }

    fun discardCard(playerId: String, cardId: String) {
        val request = DiscardCardRequest(playerId, cardId)
        WebSocketManager.sendCommand("DISCARD_CARD", JSONObject(request.toJson()))
    }

    fun requestValidPositions(cardId: String, isRotated: Boolean) {
        val payload = JSONObject().apply {
            put("cardId", cardId)
            put("isRotated", isRotated)
        }
        WebSocketManager.sendCommand("GET_VALID_POSITIONS", payload)
    }

    internal fun clearValidPositions() {
        _validPositionsUpdates.tryEmit(emptyList())
    }

    fun reset() {
        _gameStateUpdates.value = GameState(players = emptyList(), currentPlayerId = null)
        _playerUpdates.value = null
        _cardsDealtUpdates.value = null
        _validPositionsUpdates.tryEmit(emptyList())
        // Reset flows by not emitting anything or emitting defaults
    }
}
