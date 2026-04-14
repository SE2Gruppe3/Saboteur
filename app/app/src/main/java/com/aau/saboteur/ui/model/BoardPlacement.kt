package com.aau.saboteur.ui.model

import com.aau.saboteur.model.TunnelCard

data class BoardPosition(
    val row: Int,
    val column: Int
)

data class BoardPlacement(
    val position: BoardPosition,
    val card: TunnelCard
)
