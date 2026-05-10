package com.aau.saboteur.network.game

import com.aau.saboteur.model.CreateGameRequest
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.network.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    val errorMessages = WebSocketManager.errorMessages

    init {
        observeWebSocketMessages()
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
                }
            }
        }
    }

    fun startGame(players: List<Player>) {
        val request = CreateGameRequest(players = players)
        val data = JSONObject(request.toJson())
        WebSocketManager.sendMessage("START_GAME", data)
    }
}
