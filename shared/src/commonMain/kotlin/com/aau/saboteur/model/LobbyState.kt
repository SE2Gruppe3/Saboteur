package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class LobbyState(
    val lobbyCode: String,
    val hostName: String,
    val players: List<LobbyPlayer> = emptyList(),
    val maxPlayers: Int = 10,
    val gameStarted: Boolean = false,
    val minPlayersToStart: Int = 3
)