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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aau.saboteur.R
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player
import com.aau.saboteur.network.game.MapResult

@Composable
fun GameAudio(
    gameState: GameState,
    mapResult: MapResult?,
    volume: Float,
    enabled: Boolean,
    localPlayer: Player? = null,
    soundEffectsEnabled: Boolean = enabled
) {
    GameBackgroundMusic(
        volume = volume,
        enabled = enabled
    )
    GameSoundEffects(
        gameState = gameState,
        mapResult = mapResult,
        localPlayer = localPlayer,
        volume = volume,
        enabled = soundEffectsEnabled
    )
}

@Composable
private fun GameBackgroundMusic(
    volume: Float,
    enabled: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val clampedVolume = volume.coerceIn(0f, 1f)
    val currentEnabled by rememberUpdatedState(enabled)

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

    DisposableEffect(lifecycleOwner, mediaPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> mediaPlayer?.pause()
                Lifecycle.Event.ON_RESUME -> if (currentEnabled) mediaPlayer?.start()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun GameSoundEffects(
    gameState: GameState,
    mapResult: MapResult?,
    localPlayer: Player?,
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
            detectGameSoundEffects(previous, gameState, localPlayer).forEach(::play)
        }
        previousGameState = gameState
    }

    LaunchedEffect(mapResult, enabled) {
        if (enabled && mapResult != null) {
            play(GameSoundEffect.Map)
        }
    }
}
