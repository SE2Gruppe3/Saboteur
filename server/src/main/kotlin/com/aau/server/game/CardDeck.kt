package com.aau.server.game

import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.TunnelCard

// 31 PATH cards + 9 DEAD_END cards = 40 tunnel cards total
object CardDeck {

    // Creates the full tunnel card deck matching the physical board game
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

        // PATH cards
        path(setOf(Direction.TOP, Direction.LEFT, Direction.BOTTOM), 5)
        path(setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT, Direction.BOTTOM), 5)
        path(setOf(Direction.TOP, Direction.RIGHT), 5)
        path(setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT), 5)
        path(setOf(Direction.LEFT, Direction.RIGHT), 3)
        path(setOf(Direction.TOP, Direction.LEFT), 4)
        path(setOf(Direction.TOP, Direction.BOTTOM), 4)

        // DEAD_END cards
        deadEnd(setOf(Direction.TOP, Direction.BOTTOM))
        deadEnd(setOf(Direction.LEFT, Direction.RIGHT))
        deadEnd(setOf(Direction.BOTTOM))
        deadEnd(setOf(Direction.TOP, Direction.LEFT, Direction.BOTTOM))
        deadEnd(setOf(Direction.TOP, Direction.LEFT))
        deadEnd(setOf(Direction.LEFT))
        deadEnd(setOf(Direction.LEFT, Direction.RIGHT, Direction.BOTTOM))
        deadEnd(setOf(Direction.LEFT, Direction.BOTTOM))
        deadEnd(setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT, Direction.BOTTOM))

        return cards
    }

    // Creates the 3 face-down goal cards (1× gold, 2× stone)
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