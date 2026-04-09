package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class WsMessage<T>(
    val type: String,
    val data: T
)

