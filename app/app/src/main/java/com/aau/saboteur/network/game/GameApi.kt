package com.aau.saboteur.network.game

import com.aau.saboteur.network.WebSocketManager
import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CreateGameRequest
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.TunnelCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

object GameApi {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _gameStateUpdates = MutableSharedFlow<GameState>(replay = 1, extraBufferCapacity = 10)
    val gameStateUpdates: SharedFlow<GameState> = _gameStateUpdates.asSharedFlow()

    private val _playerUpdates = MutableSharedFlow<Player>(replay = 1, extraBufferCapacity = 10)
    val playerUpdates: SharedFlow<Player> = _playerUpdates.asSharedFlow()
    private val _cardsDealtUpdates = MutableSharedFlow<Map<String, List<TunnelCard>>>(replay = 0, extraBufferCapacity = 10)
    val cardsDealtUpdates: SharedFlow<Map<String, List<TunnelCard>>> = _cardsDealtUpdates.asSharedFlow()

    val errorMessages: SharedFlow<String> = WebSocketManager.errorMessages

    init {
        observeWebSocketMessages()
    }

    private fun observeWebSocketMessages() {
        scope.launch {
            WebSocketManager.messages.collect { (type, data) ->
                when {
                    type == "GAME_STATE_UPDATE" -> {
                        try {
                            val newState = data.toGameState()
                            _gameStateUpdates.tryEmit(newState)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    type.startsWith("PLAYER_DATA_") -> {
                        try {
                            val player = data.toPlayer()
                            _playerUpdates.tryEmit(player)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    type == "CARDS_DEALT" -> {
                        try {
                            val hands = data.toHands()
                            _cardsDealtUpdates.tryEmit(hands)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun startGame(players: List<Player>) {
        val request = CreateGameRequest(players = players)
        val data = JSONObject(request.toJson())
        WebSocketManager.sendMessage("START_GAME", data)
    }

    fun playCard(playerId: String, cardId: String, position: BoardPosition) {
        val data = JSONObject().apply {
            put("playerId", playerId)
            put("cardId", cardId)
            put("row", position.row)
            put("column", position.column)
        }
        WebSocketManager.sendMessage("PLAY_CARD", data)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun reset() {
        _gameStateUpdates.resetReplayCache()
        _playerUpdates.resetReplayCache()
    }
}
