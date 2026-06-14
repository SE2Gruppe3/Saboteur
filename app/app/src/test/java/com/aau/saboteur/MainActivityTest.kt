package com.aau.saboteur

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aau.saboteur.cheat.VolumeKeyDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MainActivityTest {

    @Test
    fun onCreate_setsInitialContentWithoutVolumeKeyHandler() {
        val activity = Robolectric.buildActivity(MainActivity::class.java)
            .setup()
            .get()

        assertFalse(activity.hasVolumeKeyCheatHandler())
    }

    @Test
    fun registerVolumeKeyCheatHandler_updatesHandlerState() {
        val activity = MainActivity()

        activity.registerVolumeKeyCheatHandler { true }
        assertTrue(activity.hasVolumeKeyCheatHandler())

        activity.registerVolumeKeyCheatHandler(null)
        assertFalse(activity.hasVolumeKeyCheatHandler())
    }

    @Test
    fun onKeyDown_volumeUpInvokesRegisteredHandler() {
        var receivedDirection: VolumeKeyDirection? = null
        val activity = MainActivity().apply {
            registerVolumeKeyCheatHandler { direction ->
                receivedDirection = direction
                true
            }
        }

        val consumed = activity.onKeyDown(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)
        )

        assertTrue(consumed)
        assertEquals(VolumeKeyDirection.UP, receivedDirection)
    }

    @Test
    fun onKeyDown_volumeDownInvokesRegisteredHandler() {
        var receivedDirection: VolumeKeyDirection? = null
        val activity = MainActivity().apply {
            registerVolumeKeyCheatHandler { direction ->
                receivedDirection = direction
                false
            }
        }

        val consumed = activity.onKeyDown(
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)
        )

        assertFalse(consumed)
        assertEquals(VolumeKeyDirection.DOWN, receivedDirection)
    }

    @Test
    fun onKeyUp_volumeKeyWithRegisteredHandlerIsConsumed() {
        val activity = MainActivity().apply {
            registerVolumeKeyCheatHandler { true }
        }

        val consumed = activity.onKeyUp(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)
        )

        assertTrue(consumed)
    }

    @Test
    fun nonVolumeKeyFallsThroughToDefaultHandling() {
        val activity = Robolectric.buildActivity(MainActivity::class.java)
            .setup()
            .get()
            .apply {
            registerVolumeKeyCheatHandler { true }
        }

        val keyDownConsumed = activity.onKeyDown(
            KeyEvent.KEYCODE_A,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
        )
        val keyUpConsumed = activity.onKeyUp(
            KeyEvent.KEYCODE_A,
            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A)
        )

        assertFalse(keyDownConsumed)
        assertFalse(keyUpConsumed)
    }
}
