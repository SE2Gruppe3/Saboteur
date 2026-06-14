package com.aau.saboteur

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.aau.saboteur.cheat.LocalVolumeKeyCheatHandlerRegistrar
import com.aau.saboteur.cheat.VolumeKeyCheatHandler
import com.aau.saboteur.cheat.VolumeKeyDirection
import com.aau.saboteur.navigation.AppNavHost
import com.aau.saboteur.ui.theme.SE2GameTheme
import com.aau.saboteur.util.LanguageManager
import com.aau.saboteur.util.rememberLocalizedContext

class MainActivity : ComponentActivity() {
    private var volumeKeyCheatHandler: VolumeKeyCheatHandler? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyToContext(newBase))
    }

    internal fun registerVolumeKeyCheatHandler(handler: VolumeKeyCheatHandler?) {
        volumeKeyCheatHandler = handler
    }

    internal fun hasVolumeKeyCheatHandler(): Boolean = volumeKeyCheatHandler != null

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val direction = keyCode.toVolumeKeyDirection()
        val handler = volumeKeyCheatHandler
        return if (direction != null && handler != null) {
            if (event.repeatCount > 0) true else handler(direction)
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode.toVolumeKeyDirection() != null && volumeKeyCheatHandler != null) {
            true
        } else {
            super.onKeyUp(keyCode, event)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageManager.init(this)
        enableEdgeToEdge()
        setContent {
            val language by LanguageManager.currentLanguage
            val localizedContext = rememberLocalizedContext(language)
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalVolumeKeyCheatHandlerRegistrar provides ::registerVolumeKeyCheatHandler
            ) {
                SE2GameTheme {
                    Surface {
                        val navController = rememberNavController()
                        AppNavHost(navController = navController)
                    }
                }
            }
        }
    }
}

private fun Int.toVolumeKeyDirection(): VolumeKeyDirection? = when (this) {
    KeyEvent.KEYCODE_VOLUME_UP -> VolumeKeyDirection.UP
    KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKeyDirection.DOWN
    else -> null
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SE2GameTheme {
        val navController = rememberNavController()
        AppNavHost(navController = navController)
    }
}
