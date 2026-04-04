package com.aau.saboteur.logic

import com.aau.saboteur.model.Card
import com.aau.saboteur.model.GamePhase
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Position

/**
 * Verwaltet die Spiellogik und den Spielfluss
 */
class GameManager {

    private var gameState = GameState()
    private val pathValidator = PathValidator()

    /**
     * Initialisiert ein neues Spiel
     */
    fun startNewGame() {
        gameState = GameState(
            placedCards = emptyMap(),
            currentPhase = GamePhase.SETUP,
            isValid = true
        )
    }

    /**
     * Platziert eine Karte auf dem Spielfeld
     * @param card Die zu platzierende Karte
     * @param position Die Position wo die Karte platziert werden soll
     * @return True wenn erfolgreich platziert, False sonst
     */
    fun placeCard(card: Card, position: Position): Boolean {
        // Prüfe ob Karte platzierbar ist
        if (!pathValidator.isCardPlaceable(card, position, gameState)) {
            return false
        }

        // Füge Karte zu placedCards hinzu
        val newPlacedCards = gameState.placedCards.toMutableMap()
        newPlacedCards[position] = card

        // Update GameState
        gameState = gameState.copy(
            placedCards = newPlacedCards,
            isValid = pathValidator.validateGameState(gameState)
        )

        return true
    }

    /**
     * Gibt den aktuellen Spielzustand zurück
     */
    fun getGameState(): GameState = gameState

    /**
     * Wechselt die Spielphase
     */
    fun setGamePhase(phase: GamePhase) {
        gameState = gameState.copy(currentPhase = phase)
    }

    /**
     * Validiert den aktuellen Spielzustand
     * @return True wenn der Zustand gültig ist
     */
    fun validateGame(): Boolean {
        val isValid = pathValidator.validateGameState(gameState)
        gameState = gameState.copy(isValid = isValid)
        return isValid
    }

    /**
     * Gibt eine Karte an einer Position zurück
     */
    fun getCardAt(position: Position): Card? = gameState.getCardAt(position)

    /**
     * Gibt alle Nachbarn einer Position zurück
     */
    fun getNeighbors(position: Position) = gameState.getNeighbors(position)

    /**
     * Setzt das Spiel zurück
     */
    fun resetGame() {
        startNewGame()
    }
}