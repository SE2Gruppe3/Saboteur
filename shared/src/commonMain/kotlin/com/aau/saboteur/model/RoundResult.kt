package com.aau.saboteur.model

import kotlinx.serialization.Serializable

/**
 * Ergebnis einer abgeschlossenen Spielrunde.
 *
 * Speichert die Gewinner-Rolle, welche Spieler gewonnen haben,
 * wie das Gold verteilt wurde und den Spielstand nach der Runde.
 *
 * Wird in [GameState.lastRoundResult] persistiert und an den Client gesendet.
 */


@Serializable
data class RoundResult(
    val roundNumber: Int,
    val winnerRole: Role? = null,
    val winningPlayerIds: List<String> = emptyList(),
    val revealedRoles: Map<String, Role> = emptyMap(),
    val distributedGold: Map<String, List<GoldCard>> = emptyMap(),
    val playerGoldTotals: Map<String, Int> = emptyMap(),
    val gameFinished: Boolean = false,
    val finalWinnerIds: List<String> = emptyList()
)
