package com.aau.server.game

import com.aau.saboteur.model.CardType
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
    // XOR rule: invalid only if exactly one shared edge side connects (open tunnel);
    // both-connect and both-wall are valid,
    // and the cell must be reachable from the start card via PATH cards.
    fun canPlaceCard(x: Int, y: Int, card: TunnelCard): Boolean {
        val pos = Pair(x, y)
        if (grid.containsKey(pos)) return false

        val neighbors = mapOf(
            Direction.TOP    to grid[Pair(x, y - 1)],
            Direction.RIGHT  to grid[Pair(x + 1, y)],
            Direction.BOTTOM to grid[Pair(x, y + 1)],
            Direction.LEFT   to grid[Pair(x - 1, y)]
        )

        if (neighbors.values.none { it != null }) return false

        // XOR rule: invalid if exactly one side connects (open tunnel). Both connect or both wall → valid.
        val adjacencyOk = neighbors.all { (direction, neighbor) ->
            if (neighbor == null) true
            else {
                val cardConnects = direction in card.connections
                val neighborConnects = opposite(direction) in neighbor.connections
                cardConnects == neighborConnects
            }
        }
        if (!adjacencyOk) return false

        return isReachableFromStart(x, y)
    }

    // BFS from startPosition through START, PATH, and revealed GOAL cards; returns true if (x,y) is
    // adjacent to at least one reachable card that connects toward it.
    private fun isReachableFromStart(x: Int, y: Int): Boolean {
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startPosition)
        visited.add(startPosition)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentCard = grid[current] ?: continue
            for (dir in Direction.values()) {
                val neighborPos = gridNeighbor(current, dir)
                if (neighborPos in visited) continue
                val neighborCard = grid[neighborPos] ?: continue
                val traversable = neighborCard.type == CardType.PATH ||
                    neighborCard.type == CardType.START ||
                    (neighborCard.type == CardType.GOAL && neighborCard.isRevealed)
                if (!traversable) continue
                if (dir in currentCard.connections && opposite(dir) in neighborCard.connections) {
                    visited.add(neighborPos)
                    queue.add(neighborPos)
                }
            }
        }

        val target = Pair(x, y)
        return Direction.values().any { dir ->
            val neighborPos = gridNeighbor(target, dir)
            neighborPos in visited && opposite(dir) in (grid[neighborPos]?.connections ?: emptySet())
        }
    }

    private fun gridNeighbor(pos: Pair<Int, Int>, dir: Direction): Pair<Int, Int> = when (dir) {
        Direction.TOP    -> Pair(pos.first, pos.second - 1)
        Direction.BOTTOM -> Pair(pos.first, pos.second + 1)
        Direction.LEFT   -> Pair(pos.first - 1, pos.second)
        Direction.RIGHT  -> Pair(pos.first + 1, pos.second)
    }

    private fun opposite(dir: Direction): Direction = when (dir) {
        Direction.TOP    -> Direction.BOTTOM
        Direction.BOTTOM -> Direction.TOP
        Direction.LEFT   -> Direction.RIGHT
        Direction.RIGHT  -> Direction.LEFT
    }
}