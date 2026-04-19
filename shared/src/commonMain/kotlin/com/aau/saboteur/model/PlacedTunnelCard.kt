package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class PlacedTunnelCard(
    val position: BoardPosition,
    val card: TunnelCard
)
