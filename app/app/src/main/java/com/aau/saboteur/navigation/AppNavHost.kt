package com.aau.saboteur.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aau.saboteur.screens.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val scope = rememberCoroutineScope()

    // Verwaltung des Login-Status für Ladeanzeige und Fehlermeldungen (State Hoisting)
    var isLoginLoading by remember { mutableStateOf(false) }
    var loginErrorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentRoute) {
                            "login" -> "Login"
                            "lobby" -> "Lobby"
                            "game" -> "Game"
                            "connectivity" -> "Connectivity"
                            "menu" -> "Menu"
                            else -> ""
                        }
                    )
                },
                actions = {
                    if (currentRoute != "menu") {
                        IconButton(
                            onClick = { navController.navigate("menu") },
                            modifier = Modifier.testTag("menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                // Bedingte Anpassung des Paddings zur Vermeidung von Layout-Konflikten im Login-Screen
                .padding(if (currentRoute == "login") PaddingValues(0.dp) else padding)
        ) {
            NavHost(
                navController = navController,
                startDestination = "menu",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("login") {
                    LoginScreen(
                        isLoading = isLoginLoading,
                        errorMessage = loginErrorMessage,
                        onAuthClick = { username, password, isGuest ->
                            isLoginLoading = true
                            loginErrorMessage = null

                            scope.launch {
                                // Simulation der Netzwerk-Verbindung und Fehlerbehandlung
                                delay(2000)
                                val connectionSuccessful = false

                                if (connectionSuccessful) {
                                    isLoginLoading = false
                                    navController.navigate("menu")
                                } else {
                                    isLoginLoading = false
                                    loginErrorMessage = "Keine Serververbindung! Bitte Internet prüfen."
                                }
                            }
                        }
                    )
                }
                composable("lobby") {
                    LobbyScreen()
                }
                composable("game") {
                    GameScreen()
                }
                composable("connectivity") {
                    ConnectivityTestScreen()
                }
                composable("menu") {
                    MenuScreen(navController = navController)
                }
            }
        }
    }
}