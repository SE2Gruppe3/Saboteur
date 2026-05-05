package com.aau.saboteur.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.aau.saboteur.network.game.GameApi
import com.aau.saboteur.ui.screens.*
import com.aau.saboteur.viewModels.LoginViewModel
import com.aau.saboteur.viewModels.LobbyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    remember { GameApi }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val lobbyViewModel: LobbyViewModel = viewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            currentRoute?.startsWith("menu") == true -> "Menu"
                            currentRoute == "login" -> "Login"
                            currentRoute?.startsWith("lobby") == true -> "Lobby"
                            currentRoute?.startsWith("activeLobby") == true -> "Active Lobby"
                            currentRoute == "game" -> "Game"
                            currentRoute == "connectivity" -> "Connectivity"
                            else -> ""
                        }
                    )
                },
                actions = {
                    if (currentRoute != null && !currentRoute.startsWith("menu")) {
                        IconButton(
                            onClick = { navController.navigate("menu") },
                            modifier = Modifier.testTag("menu_button")
                        ) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(if (currentRoute == "login") PaddingValues(0.dp) else padding)
        ) {
            NavHost(
                navController = navController,
                startDestination = "login",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("login") {
                    val loginViewModel: LoginViewModel = viewModel()
                    LoginScreen(
                        isLoading = loginViewModel.isLoading,
                        errorMessage = loginViewModel.errorMessage,
                        onAuthClick = { username, password, _ ->
                            loginViewModel.login(username, password) {
                                navController.navigate("menu/$username") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        }
                    )
                }

                composable(
                    route = "menu/{username}",
                    arguments = listOf(
                        navArgument("username") {
                            type = NavType.StringType
                            defaultValue = "Gast"
                        }
                    )
                ) { backStackEntry ->
                    val username = backStackEntry.arguments?.getString("username") ?: "Gast"
                    MenuScreen(navController = navController, username = username)
                }

                composable("menu") {
                    MenuScreen(navController = navController, username = "Gast")
                }

                composable(
                    route = "lobby/{username}",
                    arguments = listOf(
                        navArgument("username") {
                            type = NavType.StringType
                            defaultValue = "Gast"
                        }
                    )
                ) { backStackEntry ->
                    val username = backStackEntry.arguments?.getString("username") ?: "Gast"
                    LobbyScreen(
                        viewModel = lobbyViewModel,
                        username = username,
                        onLobbyJoined = {
                            navController.navigate("activeLobby/$username")
                        },
                        onGameStarted = {
                            navController.navigate("game")
                        }
                    )
                }

                composable(
                    route = "activeLobby/{username}",
                    arguments = listOf(
                        navArgument("username") {
                            type = NavType.StringType
                            defaultValue = "Gast"
                        }
                    )
                ) { backStackEntry ->
                    val username = backStackEntry.arguments?.getString("username") ?: "Gast"
                    ActiveLobbyScreen(
                        viewModel = lobbyViewModel,
                        username = username,
                        onStartGame = {
                            navController.navigate("game")
                        }
                    )
                }

                composable("game") {
                    GameScreen(lobbyViewModel = lobbyViewModel)
                }

                composable("connectivity") {
                    ConnectivityTestScreen()
                }
            }
        }
    }
}
