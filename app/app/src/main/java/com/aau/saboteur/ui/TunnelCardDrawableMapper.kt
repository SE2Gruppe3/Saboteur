package com.aau.saboteur.ui

import com.aau.saboteur.R
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.TunnelCard

fun TunnelCard.toDrawableName(): String = when (type) {

    CardType.CART_RED -> "cart_red"
    CardType.CART_GREEN -> "cart_green"
    CardType.LANTERN_RED -> "lantern_red"
    CardType.LANTERN_GREEN -> "lantern_green"
    CardType.PICKAXE_RED -> "pickaxe_red"
    CardType.PICKAXE_GREEN -> "pickaxe_green"
    CardType.MAPCARD -> "mapcard" // Zurück auf den Original-Namen
    CardType.ROCKFALL -> "rockfall" // Zurück auf den Original-Namen
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

// Returns the drawable name for the card's pre-rotation orientation so the
// same asset is always used. The caller must apply Modifier.rotate(180f) when
// card.isRotated is true to provide the visual transform.
fun TunnelCard.toCanonicalDrawableName(): String =
    if (!isRotated) toDrawableName()
    else copy(
        connections = connections.map { dir ->
            when (dir) {
                Direction.TOP    -> Direction.BOTTOM
                Direction.BOTTOM -> Direction.TOP
                Direction.LEFT   -> Direction.RIGHT
                Direction.RIGHT  -> Direction.LEFT
            }
        }.toSet(),
        isRotated = false
    ).toDrawableName()

fun TunnelCard.toDrawableRes(): Int = drawableNameToResId(toDrawableName())

fun TunnelCard.toCanonicalDrawableRes(): Int =
    if (!isRotated) toDrawableRes()
    else copy(
        connections = connections.map { dir ->
            when (dir) {
                Direction.TOP    -> Direction.BOTTOM
                Direction.BOTTOM -> Direction.TOP
                Direction.LEFT   -> Direction.RIGHT
                Direction.RIGHT  -> Direction.LEFT
            }
        }.toSet(),
        isRotated = false
    ).toDrawableRes()

private fun drawableNameToResId(name: String): Int = when (name) {
    "cart_red" -> R.drawable.cart_red
    "cart_green" -> R.drawable.cart_green
    "lantern_red" -> R.drawable.lantern_red
    "lantern_green" -> R.drawable.lantern_green
    "pickaxe_red" -> R.drawable.pickaxe_red
    "pickaxe_green" -> R.drawable.pickaxe_green
    "mapcard" -> R.drawable.mapcard
    "rockfall" -> R.drawable.rockfall
    "double_lantern_cart" -> R.drawable.double_lantern_cart
    "double_pickaxe_cart" -> R.drawable.double_pickaxe_cart
    "double_pickaxe_lantern" -> R.drawable.double_pickaxe_lantern
    "start" -> R.drawable.start
    "goal_gold" -> R.drawable.goal_gold
    "goal_stone1" -> R.drawable.goal_stone1
    "goal_stone2" -> R.drawable.goal_stone2
    "path_cross" -> R.drawable.path_cross
    "path_lr" -> R.drawable.path_lr
    "path_tb" -> R.drawable.path_tb
    "path_tl" -> R.drawable.path_tl
    "path_tlb" -> R.drawable.path_tlb
    "path_tlr" -> R.drawable.path_tlr
    "path_tr" -> R.drawable.path_tr
    "dead_b" -> R.drawable.dead_b
    "dead_cross" -> R.drawable.dead_cross
    "dead_l" -> R.drawable.dead_l
    "dead_lb" -> R.drawable.dead_lb
    "dead_lr" -> R.drawable.dead_lr
    "dead_lrb" -> R.drawable.dead_lrb
    "dead_tb" -> R.drawable.dead_tb
    "dead_tl" -> R.drawable.dead_tl
    "dead_tlb" -> R.drawable.dead_tlb
    else -> 0
}

fun TunnelCard.toContentDescription(): String = when (type) {
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
