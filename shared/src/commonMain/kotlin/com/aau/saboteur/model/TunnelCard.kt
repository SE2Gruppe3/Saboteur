package com.aau.saboteur.model

data class TunnelCard(
    val id: String,
    val type: CardType,
    val connections: Set<Direction>,
    val isRevealed: Boolean = false,
    val isGoal: Boolean = false
) {
    // Returns a 180°-rotated copy of the card (new id gets suffix "_r")
    fun rotated180(): TunnelCard = copy(
        id = "${id}_r",
        connections = connections.map { direction ->
            when (direction) {
                Direction.TOP    -> Direction.BOTTOM
                Direction.BOTTOM -> Direction.TOP
                Direction.LEFT   -> Direction.RIGHT
                Direction.RIGHT  -> Direction.LEFT
            }
        }.toSet()
    )
}
