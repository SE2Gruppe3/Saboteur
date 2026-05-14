package com.aau.server.websocket.event

import com.aau.saboteur.model.*

sealed class GameEvent(val type: String, val payload: Any) {
    data class LobbyStateUpdate(val lobby: LobbyState) : GameEvent("LOBBY_STATE_UPDATE", lobby)
    data class LobbyListUpdate(val lobbies: List<LobbyState>) : GameEvent("LOBBY_LIST_UPDATE", lobbies)
    data class GameStateUpdate(val state: GameState) : GameEvent("GAME_STATE_UPDATE", state)
    data class PlayerDataUpdate(val player: Player) : GameEvent("PLAYER_DATA", player)
    data class CardsDealt(val hands: Map<String, List<TunnelCard>>) : GameEvent("CARDS_DEALT", hands)
    data class GameOver(val winner: String) : GameEvent("GAME_OVER", mapOf("winner" to winner))
    data class ErrorEvent(val message: String) : GameEvent("ERROR", message)
    data class ValidPositions(val positions: List<BoardPosition>) : GameEvent("VALID_POSITIONS", mapOf("positions" to positions))
    data class LobbyLeft(val dummy: String = "") : GameEvent("LOBBY_LEFT", dummy)
}
