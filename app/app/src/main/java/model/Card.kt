package com.aau.saboteur.model

/**
 * Repräsentiert eine Tunnelkarte im Spiel
 * @param id Eindeutige Kartennummer
 * @param connections Set von Richtungen, in denen Tunnel-Verbindungen existieren
 */
data class Card(
    val id: String,
    val connections: Set<Direction> = emptySet()
) {
    /**
     * Prüft ob diese Karte eine Verbindung in die angegebene Richtung hat
     */
    fun hasConnection(direction: Direction): Boolean {
        return connections.contains(direction)
    }

    /**
     * Prüft ob diese Karte mit einer anderen Karte kompatibel ist
     * Kompatibel = wenn eine Karte nach NORTH zeigt, muss die nördliche Karte nach SOUTH zeigen
     */
    fun isCompatibleWith(other: Card, direction: Direction): Boolean {
        // Diese Karte muss in die Richtung zeigen
        if (!hasConnection(direction)) return false

        // Die andere Karte muss in die entgegengesetzte Richtung zeigen
        return other.hasConnection(direction.opposite())
    }

    companion object {
        // Vordefinierte Karten-Typen
        val STRAIGHT_HORIZONTAL = Card("straight_h", setOf(Direction.EAST, Direction.WEST))
        val STRAIGHT_VERTICAL = Card("straight_v", setOf(Direction.NORTH, Direction.SOUTH))
        val CORNER_NE = Card("corner_ne", setOf(Direction.NORTH, Direction.EAST))
        val CORNER_NW = Card("corner_nw", setOf(Direction.NORTH, Direction.WEST))
        val CORNER_SE = Card("corner_se", setOf(Direction.SOUTH, Direction.EAST))
        val CORNER_SW = Card("corner_sw", setOf(Direction.SOUTH, Direction.WEST))
        val T_NORTH = Card("t_north", setOf(Direction.NORTH, Direction.EAST, Direction.WEST))
        val T_SOUTH = Card("t_south", setOf(Direction.SOUTH, Direction.EAST, Direction.WEST))
        val T_EAST = Card("t_east", setOf(Direction.EAST, Direction.NORTH, Direction.SOUTH))
        val T_WEST = Card("t_west", setOf(Direction.WEST, Direction.NORTH, Direction.SOUTH))
        val CROSS = Card("cross", setOf(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST))
    }
}
