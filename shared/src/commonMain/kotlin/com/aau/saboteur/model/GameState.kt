package com.aau.saboteur.model

import kotlinx.serialization.Serializable


/**
 * Der aktuelle Spielstand einer Lobby-Instanz.
 *
 * Speichert alle Informationen, die zwischen Spielzügen persistiert werden müssen:
 * aktuelle Spieler, Board, Runde, Gewinn-Status und Rundenends-Ergebnis.
 */

@Serializable
data class GameState(
    val players: List<PlayerTurn> = emptyList(),
    val currentPlayerId: String? = null,
    val boardPlacements: List<PlacedTunnelCard> = emptyList(),
    val deckSize: Int = 0,
    val currentRound: Int = 1,
    val isRoundOver: Boolean = false,
    val isGameOver: Boolean = false,
    val lastRoundResult: RoundResult? = null
)
