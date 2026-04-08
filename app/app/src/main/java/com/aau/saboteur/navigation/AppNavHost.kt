package com.aau.saboteur.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aau.saboteur.ui.screens.ConnectivityTestScreen
import com.aau.saboteur.ui.screens.GameScreen
import com.aau.saboteur.ui.screens.LobbyScreen
import com.aau.saboteur.ui.screens.LoginScreen
import com.aau.saboteur.ui.screens.MenuScreen
import com.aau.saboteur.viewModels.LobbyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

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
                .padding(padding)
        ) {
            NavHost(
                navController = navController,
                startDestination = "menu",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("login") {
                    LoginScreen()
                }
                composable("lobby") {
                    LobbyScreen(
                        viewModel = LobbyViewModel(),
                        onBackPressed = { navController.popBackStack() },
                        onGameStarted = { navController.navigate("game") }
                    )
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