package com.aau.saboteur.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

    val lobbyViewModel: LobbyViewModel = viewModel()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
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
                        loginViewModel.login(username, password) { user ->
                            lobbyViewModel.saveIdentity(user.playerId, user.username)
                            navController.navigate("lobby/${user.username}") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    }
                )
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
                    },
                    onLeaveLobby = {
                        navController.popBackStack()
                    }
                )
            }

            composable("game") {
                GameScreen(
                    lobbyViewModel = lobbyViewModel,
                    onBackToLobby = {
                        val username = lobbyViewModel.username.value
                        lobbyViewModel.resetLobby()
                        navController.navigate("lobby/$username") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onBackToActiveLobby = {
                        val username = lobbyViewModel.username.value
                        GameApi.reset()
                        navController.navigate("activeLobby/$username") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
