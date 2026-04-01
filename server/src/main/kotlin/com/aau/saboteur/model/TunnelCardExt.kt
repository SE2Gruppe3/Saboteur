package com.aau.saboteur.model

fun TunnelCard.rotated180(): TunnelCard = copy(
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
