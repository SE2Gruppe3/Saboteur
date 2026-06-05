package com.aau.saboteur.model

import kotlinx.serialization.Serializable

/**
 * Repräsentiert einen Spieler mit seiner Rolle und Goldkarten.
 *
 * Speichert die eindeutige Spieler-ID, Namen, Rolle (Goldgräber/Saboteur)
 * und die Goldkarten, die der Spieler über mehrere Runden hinweg sammelt.
 */

@Serializable
data class Player(
    val id: String = "",
    val name: String = "",
    val hand: List<TunnelCard> = emptyList(),
    val role: Role? = null,
    val blockedTools: Set<ToolType> = emptySet(),
    val goldCards: List<GoldCard> = emptyList()
)
