package com.aau.server

import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.TunnelCard

fun TunnelCard.rotated180(): TunnelCard = copy(
    connections = connections.map { direction ->
        when (direction) {
            Direction.TOP    -> Direction.BOTTOM
            Direction.BOTTOM -> Direction.TOP
            Direction.LEFT   -> Direction.RIGHT
            Direction.RIGHT  -> Direction.LEFT
        }
    }.toSet(),
    isRotated = true
)
