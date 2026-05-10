package com.aau.server.game

import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.TunnelCard

object CardDeck {

    /**
     * Creates the complete deck including all special/action cards
     * following the official Saboteur (2004) card distribution.
     *
     * - 31 PATH cards, 9 DEAD_END cards (tunnel cards)
     * - 3x each blocking card (PICKAXE_RED, CART_RED, LANTERN_RED)
     * - 2x each repair card (PICKAXE_GREEN, CART_GREEN, LANTERN_GREEN)
     * - 1x each double repair card (DOUBLE_PICKAXE_CART, DOUBLE_LANTERN_CART, DOUBLE_PICKAXE_LANTERN)
     * - 3x rockfall, 3x mapcard
     */
    fun createTunnelDeck(): List<TunnelCard> {
        val cards = mutableListOf<TunnelCard>()
        var index = 0

        fun path(connections: Set<Direction>, count: Int) {
            repeat(count) {
                cards.add(
                    TunnelCard(
                        id = "path_${index++}",
                        type = CardType.PATH,
                        connections = connections
                    )
                )
            }
        }

        fun deadEnd(connections: Set<Direction>) {
            cards.add(
                TunnelCard(
                    id = "dead_${index++}",
                    type = CardType.DEAD_END,
                    connections = connections
                )
            )
        }

        // PATH cards (31 total)
        path(setOf(Direction.TOP, Direction.LEFT, Direction.BOTTOM), 5)
        path(setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT, Direction.BOTTOM), 5)
        path(setOf(Direction.TOP, Direction.RIGHT), 5)
        path(setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT), 5)
        path(setOf(Direction.LEFT, Direction.RIGHT), 3)
        path(setOf(Direction.TOP, Direction.LEFT), 4)
        path(setOf(Direction.TOP, Direction.BOTTOM), 4)

        // DEAD_END cards (9 total)
        deadEnd(setOf(Direction.TOP, Direction.BOTTOM))
        deadEnd(setOf(Direction.LEFT, Direction.RIGHT))
        deadEnd(setOf(Direction.BOTTOM))
        deadEnd(setOf(Direction.TOP, Direction.LEFT, Direction.BOTTOM))
        deadEnd(setOf(Direction.TOP, Direction.LEFT))
        deadEnd(setOf(Direction.LEFT))
        deadEnd(setOf(Direction.LEFT, Direction.RIGHT, Direction.BOTTOM))
        deadEnd(setOf(Direction.LEFT, Direction.BOTTOM))
        deadEnd(setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT, Direction.BOTTOM))

        // Blocking cards: 3x each
        repeat(3) { cards.add(TunnelCard("pickaxe_red_${index++}", CardType.PICKAXE_RED, emptySet())) }
        repeat(3) { cards.add(TunnelCard("cart_red_${index++}", CardType.CART_RED, emptySet())) }
        repeat(3) { cards.add(TunnelCard("lantern_red_${index++}", CardType.LANTERN_RED, emptySet())) }

        // Repair cards: 2x each
        repeat(2) { cards.add(TunnelCard("pickaxe_green_${index++}", CardType.PICKAXE_GREEN, emptySet())) }
        repeat(2) { cards.add(TunnelCard("cart_green_${index++}", CardType.CART_GREEN, emptySet())) }
        repeat(2) { cards.add(TunnelCard("lantern_green_${index++}", CardType.LANTERN_GREEN, emptySet())) }

        // Double repair cards: 1x each
        cards.add(TunnelCard("double_pickaxe_cart_${index++}", CardType.DOUBLE_PICKAXE_CART, emptySet()))
        cards.add(TunnelCard("double_lantern_cart_${index++}", CardType.DOUBLE_LANTERN_CART, emptySet()))
        cards.add(TunnelCard("double_pickaxe_lantern_${index++}", CardType.DOUBLE_PICKAXE_LANTERN, emptySet()))

        // Rockfall: 3x
        repeat(3) { cards.add(TunnelCard("rockfall_${index++}", CardType.ROCKFALL, emptySet())) }

        // Mapcard: 3x
        repeat(3) { cards.add(TunnelCard("mapcard_${index++}", CardType.MAPCARD, emptySet())) }

        return cards
    }

    /**
     * Creates the three goal cards: 1x gold, 2x stone
     */
    fun createGoalCards(): List<TunnelCard> = listOf(
        TunnelCard(
            id = "goal_gold",
            type = CardType.GOAL,
            connections = setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT, Direction.BOTTOM),
            isRevealed = false,
            isGoal = true
        ),
        TunnelCard(
            id = "goal_stone_1",
            type = CardType.GOAL,
            connections = setOf(Direction.TOP, Direction.RIGHT),
            isRevealed = false,
            isGoal = false
        ),
        TunnelCard(
            id = "goal_stone_2",
            type = CardType.GOAL,
            connections = setOf(Direction.BOTTOM, Direction.RIGHT),
            isRevealed = false,
            isGoal = false
        )
    )

    fun createStartCard(): TunnelCard = TunnelCard(
        id = "start",
        type = CardType.START,
        connections = setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT, Direction.BOTTOM),
        isRevealed = true
    )

    fun shuffled(deck: List<TunnelCard>): List<TunnelCard> = deck.shuffled()
}