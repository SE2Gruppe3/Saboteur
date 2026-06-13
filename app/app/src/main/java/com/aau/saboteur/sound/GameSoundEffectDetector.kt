package com.aau.saboteur.sound

import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.Role
import com.aau.saboteur.model.ToolType

internal enum class GameSoundEffect {
    TunnelDig,
    LanternBreak,
    PickaxeBreak,
    CartBreak,
    ToolRepair,
    Map,
    Explosion,
    GoalFlip,
    CoalFlip
}

internal fun detectGameSoundEffects(
    previous: GameState,
    current: GameState,
    localPlayer: Player? = null
): List<GameSoundEffect> {
    val roundEndEffect = detectRoundEndSoundEffect(previous, current, localPlayer)
    return roundEndEffect +
            detectBoardSoundEffects(previous, current, suppressGoalReveal = roundEndEffect.isNotEmpty()) +
            detectToolSoundEffects(previous, current)
}

private fun detectRoundEndSoundEffect(
    previous: GameState,
    current: GameState,
    localPlayer: Player?
): List<GameSoundEffect> {
    val winnerRole = current.lastRoundResult?.winnerRole ?: return emptyList()
    if (previous.lastRoundResult?.winnerRole != null) return emptyList()

    return when (localPlayer?.role) {
        winnerRole -> listOf(GameSoundEffect.GoalFlip)
        Role.GOLDDIGGER, Role.SABOTEUR -> listOf(GameSoundEffect.CoalFlip)
        null -> emptyList()
    }
}

private fun detectBoardSoundEffects(
    previous: GameState,
    current: GameState,
    suppressGoalReveal: Boolean = false
): List<GameSoundEffect> {
    val effects = mutableListOf<GameSoundEffect>()
    val previousPlacements = previous.boardPlacements.associateBy { it.position }
    val currentPlacements = current.boardPlacements.associateBy { it.position }

    val removedPositions = previousPlacements.keys - currentPlacements.keys
    if (removedPositions.isNotEmpty()) {
        effects += GameSoundEffect.Explosion
    }

    currentPlacements.forEach { (position, placement) ->
        val previousPlacement = previousPlacements[position]
        if (previousPlacement == null) {
            if (placement.card.type == CardType.PATH || placement.card.type == CardType.DEAD_END) {
                effects += GameSoundEffect.TunnelDig
            }
        } else if (
            !suppressGoalReveal &&
            previousPlacement.card.type == CardType.GOAL &&
            !previousPlacement.card.isRevealed &&
            placement.card.isRevealed
        ) {
            if (placement.card.isGoal) {
                effects += GameSoundEffect.GoalFlip
            } else {
                effects += GameSoundEffect.CoalFlip
            }
        }
    }

    return effects
}

private fun detectToolSoundEffects(
    previous: GameState,
    current: GameState
): List<GameSoundEffect> {
    val effects = mutableListOf<GameSoundEffect>()
    val previousPlayers = previous.players.associateBy { it.playerId }
    current.players.forEach { player ->
        val previousBlockedTools = previousPlayers[player.playerId]?.blockedTools ?: return@forEach
        val newlyBlockedTools = player.blockedTools - previousBlockedTools
        val repairedTools = previousBlockedTools - player.blockedTools

        newlyBlockedTools.forEach { tool ->
            when (tool) {
                ToolType.LANTERN -> effects += GameSoundEffect.LanternBreak
                ToolType.PICKAXE -> effects += GameSoundEffect.PickaxeBreak
                ToolType.CART -> effects += GameSoundEffect.CartBreak
            }
        }
        if (repairedTools.isNotEmpty()) {
            effects += GameSoundEffect.ToolRepair
        }
    }

    return effects
}
