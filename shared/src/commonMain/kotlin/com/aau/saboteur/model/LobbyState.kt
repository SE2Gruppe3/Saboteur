package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class LobbyState(
    val lobbyCode: String,
    val hostId: String,
    val players: List<Player> = emptyList(),
    val gameStarted: Boolean = false
)