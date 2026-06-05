package com.aau.saboteur.model

import kotlinx.serialization.Serializable


/**
 * Repräsentiert einen Spieler während seines Zugs in der aktuellen Runde.
 *
 * Speichert Zug-relevante Informationen: aktuelle blockierte Werkzeuge,
 * Reihenfolge im Spielzug, und den aktuellen Goldwert (calculated).
 *
 * Unterschied zu [Player]: PlayerTurn ist Round-spezifisch,
 * Player speichert persistente Daten (Rolle, akkumuliertes Gold).
 */

@Serializable
data class PlayerTurn(
    val playerId: String = "",
    val playerName: String = "",
    val turnOrder: Int = 0,
    val blockedTools: Set<ToolType> = emptySet(),
    val goldValue: Int = 0
)
