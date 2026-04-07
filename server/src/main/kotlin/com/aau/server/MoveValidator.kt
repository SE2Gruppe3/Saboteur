package com.aau.server

import com.aau.saboteur.model.TunnelCard

class MoveValidator(private val gameBoard: GameBoard) {

    /**
     * Prüft ob eine Karte an Position (x, y) platziert werden kann.
     *
     * Überprüft:
     * - Cell muss leer sein
     * - Mindestens 1 Nachbar muss existieren
     * - Verbindungen müssen mit Nachbarn passen
     */
    fun isValidMove(card: TunnelCard, x: Int, y: Int): Boolean {
        return gameBoard.canPlaceCard(x, y, card)
    }

    /**
     * Platziert eine Karte wenn der Zug gültig ist.
     *
     * @param card Die zu platzierende Karte
     * @param x X-Koordinate
     * @param y Y-Koordinate
     * @return Die platzierte Karte
     * @throws IllegalArgumentException wenn der Zug ungültig ist
     */
    fun placeCardIfValid(card: TunnelCard, x: Int, y: Int): TunnelCard {
        if (!isValidMove(card, x, y)) {
            throw IllegalArgumentException(
                "Ungültiger Zug: Karte kann nicht bei ($x, $y) platziert werden. " +
                        "Cell besetzt oder Verbindungen passen nicht."
            )
        }
        gameBoard.placeCard(x, y, card)
        return card
    }

    /**
     * Gibt den aktuellen GameBoard zurück
     */
    fun getGameBoard(): GameBoard = gameBoard
}