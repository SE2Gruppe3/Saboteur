
package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val username: String,
    val score: Int = 0
)