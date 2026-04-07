package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateGameRequest(
    val players: List<Player> = emptyList()
)

