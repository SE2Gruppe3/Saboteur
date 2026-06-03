package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class GoldCard(
    val id: String,
    val value: Int
)