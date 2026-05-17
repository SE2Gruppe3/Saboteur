package com.aau.saboteur.model

// Returns true if the card type is a blocking card.
fun CardType.isBlockCard(): Boolean = when (this) {
    CardType.PICKAXE_RED,
    CardType.LANTERN_RED,
    CardType.CART_RED -> true
    else -> false
}

// Returns true if the card type is any repair card, including double repair cards.
fun CardType.isRepairCard(): Boolean = when (this) {
    CardType.PICKAXE_GREEN,
    CardType.LANTERN_GREEN,
    CardType.CART_GREEN,
    CardType.DOUBLE_LANTERN_CART,
    CardType.DOUBLE_PICKAXE_CART,
    CardType.DOUBLE_PICKAXE_LANTERN -> true
    else -> false
}

// Returns true if the card type is a map card.
fun CardType.isMapCard(): Boolean = this == CardType.MAPCARD

// Returns true if the card type is a rockfall card.
fun CardType.isRockfallCard(): Boolean = this == CardType.ROCKFALL

// Returns the tool blocked by a blocking card, or null if the card is not a blocking card.
fun CardType.blockedTool(): ToolType? = when (this) {
    CardType.PICKAXE_RED -> ToolType.PICKAXE
    CardType.LANTERN_RED -> ToolType.LANTERN
    CardType.CART_RED -> ToolType.CART
    else -> null
}

// Returns all tools that can be repaired by this repair card.
fun CardType.repairableTools(): Set<ToolType> = when (this) {
    CardType.PICKAXE_GREEN -> setOf(ToolType.PICKAXE)
    CardType.LANTERN_GREEN -> setOf(ToolType.LANTERN)
    CardType.CART_GREEN -> setOf(ToolType.CART)
    CardType.DOUBLE_LANTERN_CART -> setOf(ToolType.LANTERN, ToolType.CART)
    CardType.DOUBLE_PICKAXE_CART -> setOf(ToolType.PICKAXE, ToolType.CART)
    CardType.DOUBLE_PICKAXE_LANTERN -> setOf(ToolType.PICKAXE, ToolType.LANTERN)
    else -> emptySet()
}

// Returns true if the card type is a playable tunnel card.
// Includes DEAD_END intentionally, because dead-end cards are still valid tunnel placements/removals in game logic.
fun CardType.isPathCardType(): Boolean = when (this) {
    CardType.PATH,
    CardType.DEAD_END -> true
    else -> false
}

// Returns true if the card type is a start or goal card.
fun CardType.isGoalOrStartCardType(): Boolean = when (this) {
    CardType.START,
    CardType.GOAL -> true
    else -> false
}

// Returns true if the card type is a goal card.
fun CardType.isGoalCardType(): Boolean = this == CardType.GOAL