package com.aau.server.game

import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.TunnelCard
import com.aau.server.game.CardDeck

class GameBoard {
    private val grid: MutableMap<Pair<Int, Int>, TunnelCard> = mutableMapOf()
    val startPosition: Pair<Int, Int> = Pair(0, 0)

    init {
        grid[startPosition] = CardDeck.createStartCard()
    }

    fun placeCard(x: Int, y: Int, card: TunnelCard) {
        grid[Pair(x, y)] = card
    }

    fun getCard(x: Int, y: Int): TunnelCard? = grid[Pair(x, y)]

    // Returns true if the card may be placed at (x,y):
    // cell must be empty, at least one neighbour must exist,
    // and all shared edges with neighbours must have matching connections.
    fun canPlaceCard(x: Int, y: Int, card: TunnelCard): Boolean {
        if (grid.containsKey(Pair(x, y))) return false

        val neighbors = mapOf(
            Direction.TOP to grid[Pair(x, y - 1)],
            Direction.RIGHT to grid[Pair(x + 1, y)],
            Direction.BOTTOM to grid[Pair(x, y + 1)],
            Direction.LEFT to grid[Pair(x - 1, y)]
        )

        val hasNeighbor = neighbors.values.any { it != null }
        if (!hasNeighbor) return false

        return neighbors.all { (direction, neighbor) ->
            if (neighbor == null) true
            else {
                val oppositeDir = when (direction) {
                    Direction.TOP    -> Direction.BOTTOM
                    Direction.BOTTOM -> Direction.TOP
                    Direction.LEFT   -> Direction.RIGHT
                    Direction.RIGHT  -> Direction.LEFT
                }
                val cardConnects = direction in card.connections
                val neighborConnects = oppositeDir in neighbor.connections
                cardConnects == neighborConnects
            }
        }
    }
}