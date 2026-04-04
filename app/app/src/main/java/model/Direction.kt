package com.aau.saboteur.model

/**
 * Enum für die vier Himmelsrichtungen
 * Verwendet für Tunnel-Verbindungen
 */
enum class Direction {
    NORTH,
    SOUTH,
    EAST,
    WEST;

    /**
     * Gibt die entgegengesetzte Richtung zurück
     * NORTH ↔ SOUTH
     * EAST ↔ WEST
     */
    fun opposite(): Direction = when (this) {
        NORTH -> SOUTH
        SOUTH -> NORTH
        EAST -> WEST
        WEST -> EAST
    }
}