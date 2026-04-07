package com.aau.saboteur.model

data class TunnelCard(
    val id: String,
    val type: CardType,
    val connections: Set<Direction>,
    val isRevealed: Boolean = false,
    val isGoal: Boolean = false,
    val isRotated: Boolean = false
)
