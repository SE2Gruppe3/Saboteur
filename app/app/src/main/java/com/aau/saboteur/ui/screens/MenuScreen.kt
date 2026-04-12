package com.aau.saboteur.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun MenuScreen(
    navController: NavHostController,
    username: String = "Gast"
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TODO: Temporäre Anzeige zur Verifizierung des Login-Erfolgs.
        // Wird in einem späteren Branch durch ein finales Header-Design ersetzt.
        Text(
            text = "Hallo, $username!",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Navigation Menu",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        val screens = listOf(
            "Login" to "login",
            "Lobby" to "lobby",
            "Game" to "game",
            "Connectivity Test" to "connectivity"
        )

        screens.forEach { (label, route) ->
            Button(
                onClick = {
                    navController.navigate(route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("nav_btn_$route")
            ) {
                Text(label)
            }
        }
    }
}