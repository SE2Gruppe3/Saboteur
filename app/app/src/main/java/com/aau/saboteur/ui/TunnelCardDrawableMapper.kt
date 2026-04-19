package com.aau.saboteur.ui

import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.TunnelCard

internal fun TunnelCard.toDrawableName(): String = when (type) {
    CardType.START -> "start"
    CardType.GOAL -> when (id) {
        "goal_gold" -> "goal_gold"
        "goal_stone_1" -> "goal_stone1"
        "goal_stone_2" -> "goal_stone2"
        else -> "goal_stone1"
    }
    else -> {
        val prefix = if (type == CardType.PATH) "path" else "dead"
        if (connections.size == 4) {
            "${prefix}_cross"
        } else {
            val suffix = buildString {
                if (Direction.TOP in connections) append('t')
                if (Direction.LEFT in connections) append('l')
                if (Direction.RIGHT in connections) append('r')
                if (Direction.BOTTOM in connections) append('b')
            }
            "${prefix}_$suffix"
        }
    }
}

internal fun TunnelCard.toContentDescription(): String = when (type) {
    CardType.START -> "Start card"
    CardType.GOAL -> if (isRevealed) "Revealed goal card" else "Hidden goal card"
    CardType.PATH -> "Path card"
    CardType.DEAD_END -> "Dead end card"
}
