package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class LobbyPlayer(
    val id: String,
    val name: String,
    val isReady: Boolean = false,
    val isHost: Boolean = false
)