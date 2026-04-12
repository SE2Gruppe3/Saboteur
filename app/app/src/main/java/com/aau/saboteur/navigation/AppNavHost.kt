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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.aau.saboteur.ui.screens.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aau.saboteur.viewModels.LoginViewModel

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
                        text = when {
                            currentRoute?.startsWith("menu") == true -> "Menu"
                            currentRoute == "login" -> "Login"
                            currentRoute == "lobby" -> "Lobby"
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
                // LOGIN ROUTE
                composable("login") {
                    val loginViewModel: LoginViewModel = viewModel()
                    LoginScreen(
                        isLoading = loginViewModel.isLoading,
                        errorMessage = loginViewModel.errorMessage,
                        onAuthClick = { username, password, _ ->
                            loginViewModel.login(username, password) {
                                // TODO: Temporäre Übergabe via Route
                                navController.navigate("menu/$username") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        }
                    )
                }

                // MENU ROUTE
                composable(
                    route = "menu/{username}",
                    arguments = listOf(navArgument("username") {
                        type = NavType.StringType
                        defaultValue = "Gast"
                    })
                ) { backStackEntry ->
                    val username = backStackEntry.arguments?.getString("username") ?: "Gast"
                    MenuScreen(navController = navController, username = username)
                }

                //FALLBACK MENU (Ohne Parameter)
                composable("menu") {
                    MenuScreen(navController = navController, username = "Gast")
                }

                // WEITERE ROUTEN
                composable("lobby") { LobbyScreen() }
                composable("game") { GameScreen() }
                composable("connectivity") { ConnectivityTestScreen() }

            }
        }
    }
}