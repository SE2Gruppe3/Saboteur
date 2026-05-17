package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class ReconnectSnapshot(
    val lobbyState: LobbyState,
    val gameState: GameState? = null,
    val playerState: Player, // The specific player receiving the snapshot
    val serverTimestamp: Long
)
