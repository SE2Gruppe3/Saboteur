package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class BoardPosition(
    val row: Int,
    val column: Int
)
