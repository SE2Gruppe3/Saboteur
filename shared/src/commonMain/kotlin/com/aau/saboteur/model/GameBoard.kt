package com.aau.saboteur.model

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

        val opposite = mapOf(
            Direction.TOP to Direction.BOTTOM,
            Direction.BOTTOM to Direction.TOP,
            Direction.LEFT to Direction.RIGHT,
            Direction.RIGHT to Direction.LEFT
        )

        return neighbors.all { (direction, neighbor) ->
            if (neighbor == null) true
            else {
                val cardConnects = direction in card.connections
                val neighborConnects = opposite[direction]!! in neighbor.connections
                cardConnects == neighborConnects
            }
        }
    }
}
