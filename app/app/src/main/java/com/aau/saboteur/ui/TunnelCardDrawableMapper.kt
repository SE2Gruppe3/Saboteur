package com.aau.saboteur.ui

import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.TunnelCard

internal fun TunnelCard.toDrawableName(): String = when (type) {

    CardType.CART_RED -> "cart_red"
    CardType.CART_GREEN -> "cart_green"
    CardType.LANTERN_RED -> "lantern_red"
    CardType.LANTERN_GREEN -> "lantern_green"
    CardType.PICKAXE_RED -> "pickaxe_red"
    CardType.PICKAXE_GREEN -> "pickaxe_green"
    CardType.MAPCARD -> "mapcard"
    CardType.ROCKFALL -> "rockfall"
    CardType.DOUBLE_LANTERN_CART -> "double_lantern_cart"
    CardType.DOUBLE_PICKAXE_CART -> "double_pickaxe_cart"
    CardType.DOUBLE_PICKAXE_LANTERN -> "double_pickaxe_lantern"

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


    CardType.CART_RED,
    CardType.CART_GREEN,
    CardType.LANTERN_RED,
    CardType.LANTERN_GREEN,
    CardType.PICKAXE_RED,
    CardType.PICKAXE_GREEN,
    CardType.ROCKFALL,
    CardType.MAPCARD,
    CardType.DOUBLE_LANTERN_CART,
    CardType.DOUBLE_PICKAXE_CART,
    CardType.DOUBLE_PICKAXE_LANTERN
        -> "Special tool card"

}
