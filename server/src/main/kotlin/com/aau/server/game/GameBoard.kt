package com.aau.server.game

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
}