package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
enum class CardType { PATH, DEAD_END, START, GOAL }
