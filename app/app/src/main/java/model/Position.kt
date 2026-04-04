package com.aau.saboteur.model

/**
 * Repräsentiert eine Position im Spielgitter
 * @param x X-Koordinate (Spalte)
 * @param y Y-Koordinate (Reihe)
 */
data class Position(
    val x: Int,
    val y: Int
) {
    /**
     * Gibt die Position in einer Richtung zurück
     */
    fun getNeighbor(direction: Direction): Position = when (direction) {
        Direction.NORTH -> Position(x, y - 1)
        Direction.SOUTH -> Position(x, y + 1)
        Direction.EAST -> Position(x + 1, y)
        Direction.WEST -> Position(x - 1, y)
    }

    /**
     * Validiert ob Position gültig ist
     */
    fun isValid(maxWidth: Int = 5, maxHeight: Int = 5): Boolean {
        return x in 0 until maxWidth && y in 0 until maxHeight
    }
}

