package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionInfo(
    val sessionId: String,
    val players: List<Player> = emptyList(),
    val gameState: GameState? = null,
    val isStarted: Boolean = false
)

@Serializable
data class ReconnectRequest(
    val playerId: String,
    val sessionId: String
)

@Serializable
data class JoinSessionRequest(
    val sessionId: String,
    val playerName: String
)
