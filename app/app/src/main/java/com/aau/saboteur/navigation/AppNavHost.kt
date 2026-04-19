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
import com.aau.saboteur.ui.screens.ConnectivityTestScreen
import com.aau.saboteur.ui.screens.GameScreen
import com.aau.saboteur.ui.screens.LoginScreen
import com.aau.saboteur.ui.screens.LobbyScreen
import com.aau.saboteur.ui.screens.MenuScreen
import com.aau.saboteur.viewModels.LoginViewModel
import com.aau.saboteur.viewModels.LobbyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

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
                    // Zeige Menü-Icon auf allen Screens außer dem Menü selbst (und nur wenn wir eine Route haben)
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
                startDestination = "login", // zum Testen kannst du hier "lobby" setzen
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

                // FALLBACK MENU (Ohne Parameter)
                composable("menu") {
                    MenuScreen(navController = navController, username = "Gast")
                }

                // LOBBY ROUTE (WICHTIG: echtes ViewModel aus dem Lifecycle holen)
                composable("lobby") {
                    val lobbyViewModel: LobbyViewModel = viewModel()
                    LobbyScreen(
                        viewModel = lobbyViewModel,
                        onBackPressed = { navController.popBackStack() },
                        onGameStarted = { navController.navigate("game") }
                    )
                }

                // GAME ROUTE
                composable("game") {
                    GameScreen()
                }

                // CONNECTIVITY TEST ROUTE
                composable("connectivity") {
                    ConnectivityTestScreen()
                }
            }
        }
    }
}