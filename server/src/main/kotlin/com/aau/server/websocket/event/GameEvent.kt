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
    data class MapResultEvent(val result: MapResult) : GameEvent("MAP_RESULT", result)
    data class LobbyLeft(val dummy: String = "") : GameEvent("LOBBY_LEFT", dummy)
    data class PlayerKicked(val playerId: String) : GameEvent("PLAYER_KICKED", mapOf("playerId" to playerId))
    data class SyncComplete(val dummy: String = "") : GameEvent("SYNC_COMPLETE", dummy)
    data class ReconnectSnapshotEvent(val snapshot: ReconnectSnapshot) : GameEvent("RECONNECT_SNAPSHOT", snapshot)
    data class LobbyNotFound(val message: String = "Lobby nach Serverneustart abgelaufen") : GameEvent("LOBBY_NOT_FOUND", message)
}
