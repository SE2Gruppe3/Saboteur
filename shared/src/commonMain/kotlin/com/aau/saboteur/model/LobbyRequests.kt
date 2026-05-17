package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class LobbyCreateRequest(val playerName: String, val playerId: String? = null)

@Serializable
data class LobbyJoinRequest(val lobbyCode: String, val playerName: String, val playerId: String? = null)

@Serializable
data class LobbyLeaveRequest(val lobbyCode: String, val playerId: String)

@Serializable
data class ReconnectRequest(val playerId: String, val lobbyCode: String)

@Serializable
data class ReconnectResponse(
    val myPlayerId: String,
    val lobbyState: LobbyState,
    val gameState: GameState? = null,
    val playerHand: List<TunnelCard> = emptyList(),
    val playerRole: Role? = null
)
