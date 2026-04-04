package com.aau.saboteur.logic

import com.aau.saboteur.model.Card
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Position

/**
 * Validiert die Kompatibilität von Tunnelkarten im Spielgitter
 */
class PathValidator {

    /**
     * Prüft ob eine Karte an einer bestimmten Position platzierbar ist
     * @param card Die zu platzierende Karte
     * @param position Die Position wo die Karte platziert werden soll
     * @param gameState Der aktuelle Spielzustand
     * @return True wenn die Karte platzierbar ist, False sonst
     */
    fun isCardPlaceable(card: Card, position: Position, gameState: GameState): Boolean {
        // Position muss gültig sein
        if (!position.isValid()) return false

        // Position darf nicht bereits belegt sein
        if (gameState.isPositionOccupied(position)) return false

        // Nachbarn prüfen
        val neighbors = gameState.getNeighbors(position)

        for ((direction, neighborCard) in neighbors) {
            // Wenn es einen Nachbarn gibt, muss die Karte kompatibel sein
            if (!card.isCompatibleWith(neighborCard, direction)) {
                return false
            }
        }

        return true
    }

    /**
     * Prüft ob zwei Karten kompatibel sind
     * @param card1 Erste Karte
     * @param card2 Zweite Karte
     * @param direction Richtung von card1 zu card2
     * @return True wenn kompatibel, False sonst
     */
    fun areCardsCompatible(card1: Card, card2: Card, direction: Direction): Boolean {
        return card1.isCompatibleWith(card2, direction)
    }

    /**
     * Validiert den gesamten aktuellen Spielzustand
     * @param gameState Der zu validierende Spielzustand
     * @return True wenn alle Verbindungen gültig sind
     */
    fun validateGameState(gameState: GameState): Boolean {
        for ((position, card) in gameState.placedCards) {
            val neighbors = gameState.getNeighbors(position)

            for ((direction, neighborCard) in neighbors) {
                if (!card.isCompatibleWith(neighborCard, direction)) {
                    return false
                }
            }
        }

        return true
    }
}