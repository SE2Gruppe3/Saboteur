package com.aau.saboteur.model
import kotlinx.serialization.Serializable

@Serializable
data class TunnelCard(
    val id: String,
    val type: CardType,
    // Always stores effective (post-rotation) values. Placement logic reads
    // connections directly and never re-applies isRotated.
    val connections: Set<Direction>,
    val isRevealed: Boolean = false,
    val isGoal: Boolean = false,
    // Render-only flag. The UI draws the card rotated 180°; connections already
    // reflect the post-rotation state and must not be flipped again on read.
    val isRotated: Boolean = false
)
