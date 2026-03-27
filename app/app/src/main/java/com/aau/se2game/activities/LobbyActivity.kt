package com.aau.se2game.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aau.se2game.ui.theme.SE2GameTheme
import com.aau.se2game.navigation.NavigationMenu

class LobbyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SE2GameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box {
                        LobbyScreen()
                        NavigationMenu()
                    }
                }
            }
        }
    }
}

@Composable
fun LobbyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Lobby", style = MaterialTheme.typography.headlineMedium)
    }
}
