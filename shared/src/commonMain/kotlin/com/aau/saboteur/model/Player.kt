package com.aau.saboteur.model

data class Player(
    val id: String,
    val name: String,
    val hand: List<TunnelCard> = emptyList()
)
