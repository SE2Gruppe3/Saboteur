package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val players: List<PlayerTurn> = emptyList(),
    val currentPlayerId: String? = null,
    val boardPlacements: List<PlacedTunnelCard> = emptyList()
)
