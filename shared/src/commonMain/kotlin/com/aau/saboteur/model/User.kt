package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long? = null,
    val username: String = "",
    val passwordHash: String = "",
    val playerId: String = "" // UUID string for game identity
)
