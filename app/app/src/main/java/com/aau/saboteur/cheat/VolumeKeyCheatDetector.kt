package com.aau.saboteur.cheat

import androidx.compose.runtime.staticCompositionLocalOf

enum class VolumeKeyDirection {
    UP,
    DOWN
}

typealias VolumeKeyCheatHandler = (VolumeKeyDirection) -> Boolean

val LocalVolumeKeyCheatHandlerRegistrar =
    staticCompositionLocalOf<(VolumeKeyCheatHandler?) -> Unit> { {} }

class VolumeKeyCheatDetector(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val timeoutMillis: Long = 2_000L
) {
    private val targetSequence = listOf(
        VolumeKeyDirection.UP,
        VolumeKeyDirection.UP,
        VolumeKeyDirection.DOWN,
        VolumeKeyDirection.DOWN
    )
    private val pressedKeys = ArrayDeque<Pair<VolumeKeyDirection, Long>>()

    fun onKeyPressed(direction: VolumeKeyDirection): Boolean {
        val now = nowMillis()
        pressedKeys.addLast(direction to now)

        while (pressedKeys.isNotEmpty() && now - pressedKeys.first().second > timeoutMillis) {
            pressedKeys.removeFirst()
        }
        while (pressedKeys.size > targetSequence.size) {
            pressedKeys.removeFirst()
        }

        val matched = pressedKeys.map { it.first } == targetSequence
        if (matched) {
            pressedKeys.clear()
        }
        return matched
    }
}
