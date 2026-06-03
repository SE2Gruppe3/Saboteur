package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: String = "",
    val name: String = "",
    val hand: List<TunnelCard> = emptyList(),
    val role: Role? = null,
    val blockedTools: Set<ToolType> = emptySet(),
    val goldCards: List<GoldCard> = emptyList()
)