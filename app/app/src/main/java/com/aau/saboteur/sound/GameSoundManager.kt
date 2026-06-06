package com.aau.saboteur.sound

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aau.saboteur.R
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.ToolType
import com.aau.saboteur.network.game.MapResult

private enum class GameSoundEffect {
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

@Composable
fun GameAudio(
    gameState: GameState,
    mapResult: MapResult?,
    volume: Float,
    enabled: Boolean
) {
    GameBackgroundMusic(
        volume = volume,
        enabled = enabled
    )
    GameSoundEffects(
        gameState = gameState,
        mapResult = mapResult,
        volume = volume,
        enabled = enabled
    )
}

@Composable
private fun GameBackgroundMusic(
    volume: Float,
    enabled: Boolean
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val clampedVolume = volume.coerceIn(0f, 1f)

    DisposableEffect(context, enabled) {
        if (!enabled) {
            mediaPlayer?.release()
            mediaPlayer = null
            return@DisposableEffect onDispose { }
        }

        mediaPlayer = MediaPlayer.create(context, R.raw.game_background_music)?.apply {
            isLooping = true
            setVolume(clampedVolume, clampedVolume)
            start()
        }

        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(clampedVolume, mediaPlayer) {
        mediaPlayer?.setVolume(clampedVolume, clampedVolume)
    }
}

@Composable
private fun GameSoundEffects(
    gameState: GameState,
    mapResult: MapResult?,
    volume: Float,
    enabled: Boolean
) {
    val context = LocalContext.current
    val clampedVolume = volume.coerceIn(0f, 1f)
    val soundPool = remember {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(attributes)
            .build()
    }
    val soundIds = remember(soundPool) {
        mapOf(
            GameSoundEffect.TunnelDig to soundPool.load(context, R.raw.sfx_tunnel_dig, 1),
            GameSoundEffect.LanternBreak to soundPool.load(context, R.raw.sfx_lantern_break, 1),
            GameSoundEffect.PickaxeBreak to soundPool.load(context, R.raw.sfx_pickaxe_break, 1),
            GameSoundEffect.CartBreak to soundPool.load(context, R.raw.sfx_cart_break, 1),
            GameSoundEffect.ToolRepair to soundPool.load(context, R.raw.sfx_tool_repair, 1),
            GameSoundEffect.Map to soundPool.load(context, R.raw.sfx_map, 1),
            GameSoundEffect.Explosion to soundPool.load(context, R.raw.sfx_explosion, 1),
            GameSoundEffect.GoalFlip to soundPool.load(context, R.raw.sfx_goal_flip, 1),
            GameSoundEffect.CoalFlip to soundPool.load(context, R.raw.sfx_coal_flip, 1)
        )
    }
    var previousGameState by remember { mutableStateOf<GameState?>(null) }

    fun play(effect: GameSoundEffect) {
        if (!enabled) return
        val soundId = soundIds[effect] ?: return
        soundPool.play(soundId, clampedVolume, clampedVolume, 1, 0, 1f)
    }

    DisposableEffect(soundPool) {
        onDispose {
            soundPool.release()
        }
    }

    LaunchedEffect(gameState, enabled) {
        val previous = previousGameState
        if (enabled && previous != null) {
            playBoardSounds(previous, gameState, ::play)
            playToolSounds(previous, gameState, ::play)
        }
        previousGameState = gameState
    }

    LaunchedEffect(mapResult, enabled) {
        if (enabled && mapResult != null) {
            play(GameSoundEffect.Map)
        }
    }
}

private fun playBoardSounds(
    previous: GameState,
    current: GameState,
    play: (GameSoundEffect) -> Unit
) {
    val previousPlacements = previous.boardPlacements.associateBy { it.position }
    val currentPlacements = current.boardPlacements.associateBy { it.position }

    val removedPositions = previousPlacements.keys - currentPlacements.keys
    if (removedPositions.isNotEmpty()) {
        play(GameSoundEffect.Explosion)
    }

    currentPlacements.forEach { (position, placement) ->
        val previousPlacement = previousPlacements[position]
        if (previousPlacement == null) {
            if (placement.card.type == CardType.PATH || placement.card.type == CardType.DEAD_END) {
                play(GameSoundEffect.TunnelDig)
            }
        } else if (
            previousPlacement.card.type == CardType.GOAL &&
            !previousPlacement.card.isRevealed &&
            placement.card.isRevealed
        ) {
            if (placement.card.isGoal) {
                play(GameSoundEffect.GoalFlip)
            } else {
                play(GameSoundEffect.CoalFlip)
            }
        }
    }
}

private fun playToolSounds(
    previous: GameState,
    current: GameState,
    play: (GameSoundEffect) -> Unit
) {
    val previousPlayers = previous.players.associateBy { it.playerId }
    current.players.forEach { player ->
        val previousBlockedTools = previousPlayers[player.playerId]?.blockedTools ?: return@forEach
        val newlyBlockedTools = player.blockedTools - previousBlockedTools
        val repairedTools = previousBlockedTools - player.blockedTools

        newlyBlockedTools.forEach { tool ->
            when (tool) {
                ToolType.LANTERN -> play(GameSoundEffect.LanternBreak)
                ToolType.PICKAXE -> play(GameSoundEffect.PickaxeBreak)
                ToolType.CART -> play(GameSoundEffect.CartBreak)
            }
        }
        if (repairedTools.isNotEmpty()) {
            play(GameSoundEffect.ToolRepair)
        }
    }
}
