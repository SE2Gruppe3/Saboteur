package com.aau.saboteur.network.game

import com.aau.saboteur.model.*
import com.aau.saboteur.network.WebSocketManager
import com.aau.saboteur.network.lobby.LobbyApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class MapResult(
    val position: BoardPosition,
    val card: TunnelCard
)

object GameApi {
    private var dispatcher: CoroutineDispatcher = Dispatchers.IO
    private var scope = CoroutineScope(SupervisorJob() + dispatcher)

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

    private val _mapResultEvents = MutableSharedFlow<MapResult>(replay = 0, extraBufferCapacity = 1)
    val mapResultEvents: SharedFlow<MapResult> = _mapResultEvents.asSharedFlow()

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

        WebSocketManager.onEvent("MAP_RESULT") { data ->
            runCatching { data.toMapResult() }
                .onSuccess { _mapResultEvents.tryEmit(it) }
                .onFailure { it.printStackTrace() }
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

                val currentHands = _cardsDealtUpdates.value ?: emptyMap()
                _cardsDealtUpdates.value = currentHands + (data.myPlayerId to data.playerHand)
            }
        }
    }

    internal fun resetObserversForTesting(testDispatcher: CoroutineDispatcher) {
        scope.cancel()
        dispatcher = testDispatcher
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        observeConnectionErrors()
        observeLobbyReconnects()
    }

    fun startGame(lobbyCode: String, players: List<Player>) {
        val request = CreateGameRequest(players = players)
        val payload = JSONObject(request.toJson()).apply {
            put("lobbyCode", lobbyCode)
        }
        WebSocketManager.sendCommand("START_GAME", payload)
    }

    fun playCard(lobbyCode: String, playerId: String, cardId: String, position: BoardPosition, isRotated: Boolean) {
        val request = PlayCardRequest(playerId, cardId, position, isRotated)
        val payload = JSONObject(request.toJson()).apply {
            put("lobbyCode", lobbyCode)
        }
        WebSocketManager.sendCommand("PLAY_CARD", payload)
    }

    fun discardCard(lobbyCode: String, playerId: String, cardId: String) {
        val request = DiscardCardRequest(playerId, cardId)
        val payload = JSONObject(request.toJson()).apply {
            put("lobbyCode", lobbyCode)
        }
        WebSocketManager.sendCommand("DISCARD_CARD", payload)
    }

    fun requestValidPositions(lobbyCode: String, cardId: String, isRotated: Boolean) {
        val payload = JSONObject().apply {
            put("cardId", cardId)
            put("isRotated", isRotated)
            put("lobbyCode", lobbyCode)
        }
        WebSocketManager.sendCommand("GET_VALID_POSITIONS", payload)
    }

    fun playBlockCard(lobbyCode: String, playerId: String, cardId: String, targetPlayerId: String) {
        val payload = JSONObject().apply {
            put("lobbyCode", lobbyCode)
            put("playerId", playerId)
            put("cardId", cardId)
            put("targetPlayerId", targetPlayerId)
        }
        WebSocketManager.sendCommand("PLAY_BLOCK_CARD", payload)
    }

    fun playRepairCard(lobbyCode: String, playerId: String, cardId: String, targetPlayerId: String, tool: String) {
        val payload = JSONObject().apply {
            put("lobbyCode", lobbyCode)
            put("playerId", playerId)
            put("cardId", cardId)
            put("targetPlayerId", targetPlayerId)
            put("tool", tool)
        }
        WebSocketManager.sendCommand("PLAY_REPAIR_CARD", payload)
    }

    fun playMapCard(lobbyCode: String, playerId: String, cardId: String, position: BoardPosition) {
        val payload = JSONObject().apply {
            put("lobbyCode", lobbyCode)
            put("playerId", playerId)
            put("cardId", cardId)
            put("targetPosition", JSONObject().apply {
                put("row", position.row)
                put("column", position.column)
            })
        }
        WebSocketManager.sendCommand("PLAY_MAP_CARD", payload)
    }

    fun playRockfallCard(lobbyCode: String, playerId: String, cardId: String, position: BoardPosition) {
        val payload = JSONObject().apply {
            put("lobbyCode", lobbyCode)
            put("playerId", playerId)
            put("cardId", cardId)
            put("targetPosition", JSONObject().apply {
                put("row", position.row)
                put("column", position.column)
            })
        }
        WebSocketManager.sendCommand("PLAY_ROCKFALL_CARD", payload)
    }

    fun triggerCheat(lobbyCode: String, cheatType: CheatType) {
        val payload = JSONObject().apply {
            put("lobbyCode", lobbyCode)
            put("cheatType", cheatType.name)
        }
        WebSocketManager.sendCommand("PLAYER_CHEAT", payload)
    }

    internal fun clearValidPositions() {
        _validPositionsUpdates.tryEmit(emptyList())
    }

    fun reset() {
        _gameStateUpdates.value = GameState(players = emptyList(), currentPlayerId = null)
        _playerUpdates.value = null
        _cardsDealtUpdates.value = null
        _validPositionsUpdates.tryEmit(emptyList())
    }

    private fun String.toMapResult(): MapResult {
        val json = JSONObject(this)
        val posJson = json.getJSONObject("position")
        val cardJson = json.getJSONObject("card")
        return MapResult(
            position = BoardPosition(posJson.getInt("row"), posJson.getInt("column")),
            card = Json.decodeFromString<TunnelCard>(cardJson.toString())
        )
    }
}
