package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class LobbyCreateRequest(val playerName: String)

@Serializable
data class LobbyJoinRequest(val lobbyCode: String, val playerName: String)

@Serializable
data class LobbyLeaveRequest(val lobbyCode: String, val playerId: String)
