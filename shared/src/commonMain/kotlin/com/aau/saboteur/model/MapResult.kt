package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class MapResult(
    val position: BoardPosition,
    val card: TunnelCard
)