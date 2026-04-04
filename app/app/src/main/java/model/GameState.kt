package com.aau.saboteur.model

/**
 * Repräsentiert den aktuellen Zustand des Spiels
 * @param placedCards Map von Position zu gelegter Karte
 * @param currentPhase Aktuelle Spielphase
 * @param isValid Ob der aktuelle Zustand gültig ist
 */
data class GameState(
    val placedCards: Map<Position, Card> = emptyMap(),
    val currentPhase: GamePhase = GamePhase.SETUP,
    val isValid: Boolean = true
) {
    /**
     * Gibt alle Karten an einer Position und deren Nachbarn zurück
     */
    fun getCardAt(position: Position): Card? = placedCards[position]

    /**
     * Prüft ob eine Position bereits eine Karte hat
     */
    fun isPositionOccupied(position: Position): Boolean = placedCards.containsKey(position)

    /**
     * Gibt alle Nachbar-Positionen einer Position zurück (mit Karten)
     */
    fun getNeighbors(position: Position): Map<Direction, Card> {
        val neighbors = mutableMapOf<Direction, Card>()

        for (direction in Direction.values()) {
            val neighborPos = position.getNeighbor(direction)
            placedCards[neighborPos]?.let {
                neighbors[direction] = it
            }
        }

        return neighbors
    }
}

/**
 * Enum für die Spielphasen
 */
enum class GamePhase {
    SETUP,           // Vorbereitung
    PLAYING,         // Spielphase läuft
    VALIDATION,      // Validierung der Wege
    FINISHED         // Spiel beendet
}
