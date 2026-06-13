package com.aau.saboteur.cheat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeKeyCheatDetectorTest {

    @Test
    fun `onKeyPressed matches UP UP DOWN DOWN sequence`() {
        var now = 1_000L
        val detector = VolumeKeyCheatDetector(nowMillis = { now })

        assertFalse(detector.onKeyPressed(VolumeKeyDirection.UP))
        now += 100
        assertFalse(detector.onKeyPressed(VolumeKeyDirection.UP))
        now += 100
        assertFalse(detector.onKeyPressed(VolumeKeyDirection.DOWN))
        now += 100
        assertTrue(detector.onKeyPressed(VolumeKeyDirection.DOWN))
    }

    @Test
    fun `onKeyPressed ignores wrong sequence`() {
        val detector = VolumeKeyCheatDetector(nowMillis = { 1_000L })

        detector.onKeyPressed(VolumeKeyDirection.UP)
        detector.onKeyPressed(VolumeKeyDirection.DOWN)
        detector.onKeyPressed(VolumeKeyDirection.UP)

        assertFalse(detector.onKeyPressed(VolumeKeyDirection.DOWN))
    }

    @Test
    fun `onKeyPressed expires sequence after timeout`() {
        var now = 1_000L
        val detector = VolumeKeyCheatDetector(nowMillis = { now }, timeoutMillis = 500L)

        detector.onKeyPressed(VolumeKeyDirection.UP)
        now += 100
        detector.onKeyPressed(VolumeKeyDirection.UP)
        now += 600
        detector.onKeyPressed(VolumeKeyDirection.DOWN)
        now += 100

        assertFalse(detector.onKeyPressed(VolumeKeyDirection.DOWN))
    }
}
