package com.aau.saboteur

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.aau.saboteur.navigation.AppNavHost
import com.aau.saboteur.ui.theme.SE2GameTheme
import com.aau.saboteur.util.LanguageManager
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyToContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageManager.currentLanguage.value = LanguageManager.getLanguage(this)
        enableEdgeToEdge()
        setContent {
            val language by LanguageManager.currentLanguage
            val baseContext = LocalContext.current
            val localizedContext = remember(language) {
                val locale = Locale(language)
                Locale.setDefault(locale)
                val config = Configuration(baseContext.resources.configuration)
                config.setLocale(locale)
                baseContext.createConfigurationContext(config)
            }
            CompositionLocalProvider(LocalContext provides localizedContext) {
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

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SE2GameTheme {
        val navController = rememberNavController()
        AppNavHost(navController = navController)
    }
}