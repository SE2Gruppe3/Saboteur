package com.aau.shared.game

import kotlinx.serialization.Serializable

@Serializable
data class WsMessage<T>(
    val type: String,
    val data: T
)
