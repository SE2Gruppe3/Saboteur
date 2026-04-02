package com.aau.saboteur.models

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val passwordHash: String? = null,
    val isGuest: Boolean = false
)

@Serializable
data class RegisterRequest(
    val username: String,
    val passwordHash: String
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val token: String? = null,
    val errorMessage: String? = null
)