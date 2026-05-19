package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
enum class Role(val displayName: String) {
    GOLDDIGGER("GOLDGRÄBER"),
    SABOTEUR("SABOTEUR")
}
