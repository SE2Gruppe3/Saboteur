package com.aau.saboteur.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aau.saboteur.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aau.saboteur.model.PlayerTurn
import com.aau.saboteur.ui.components.BoardGrid
import com.aau.saboteur.ui.components.PlayerHandRow
import com.aau.saboteur.ui.components.PlayerTurnOrderRow
import com.aau.saboteur.ui.components.RoleCardView
import com.aau.saboteur.viewModels.GameViewModel
import com.aau.saboteur.viewModels.LobbyViewModel
import kotlinx.coroutines.delay

@Composable
fun GameScreen(
    lobbyViewModel: LobbyViewModel,
    onBackToLobby: () -> Unit = {},
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val localPlayerId by lobbyViewModel.playerId.collectAsState()
    val validPositions by viewModel.validPositions.collectAsState()

    var gameOverWinner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.gameOverEvents.collect { winner -> gameOverWinner = winner }
    }
    
    val sortedPlayers = uiState.gameState.players.sortedBy(PlayerTurn::turnOrder)
    val currentHand = uiState.localPlayerId?.let { uiState.hands?.get(it) }
    val isMyTurn = uiState.localPlayerId != null &&
            uiState.gameState.currentPlayerId == uiState.localPlayerId &&
            !uiState.isSyncing

    var showTurnHint by remember { mutableStateOf(false) }

    LaunchedEffect(localPlayerId) {
        viewModel.setLocalPlayerId(localPlayerId)
    }

    LaunchedEffect(isMyTurn) {
        if (isMyTurn) {
            showTurnHint = true
            delay(2000)
            showTurnHint = false
        } else {
            showTurnHint = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BoardGrid(
            placements = uiState.gameState.boardPlacements,
            modifier = Modifier.fillMaxSize(),
            validPositions = validPositions,
            onCellClick = { position ->
                if (isMyTurn && uiState.selectedCard != null) {
                    viewModel.onBoardCellClicked(position)
                }
            }
        )

        // Top Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (sortedPlayers.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(top = 16.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
                ) {
                    PlayerTurnOrderRow(
                        players = sortedPlayers,
                        currentPlayerId = uiState.gameState.currentPlayerId
                    )
                }
            }
        }

        // Error & Hint Overlays
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.errorMessage?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Bottom Controls
        if (currentHand != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(bottom = 24.dp, top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.player?.role?.let { role ->
                    RoleCardView(role = role, compact = true)
                }

                if (isMyTurn && uiState.selectedCard != null) {
                    Button(
                        onClick = { viewModel.discardSelectedCard() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text("Karte verwerfen", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }

                PlayerHandRow(
                    hand = currentHand,
                    selectedCardId = uiState.selectedCard?.id,
                    onCardSelected = { if (isMyTurn) viewModel.selectCard(it) },
                    onCardRotated = { card, rotated -> viewModel.onCardRotated(card, rotated) }
                )
            }
        }

        // GAME OVER OVERLAY
        gameOverWinner?.let { winner ->
            GameOverDialog(winner = winner, onBackToLobby = onBackToLobby)
        }

        // SYNC OVERLAY (Reconnect Stabilität)
        if (uiState.isSyncing) {
            GameSyncOverlay()
        }
    }
}

@Composable
private fun GameSyncOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text("Reconnecting...", style = MaterialTheme.typography.headlineSmall)
                Text("Synchronizing game state with server", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun GameOverDialog(winner: String, onBackToLobby: () -> Unit) {
    val resultText = when (winner) {
        "DWARVES" -> "Zwerge gewinnen! ⛏️"
        "SABOTEURS" -> "Saboteure gewinnen! 🪓"
        else -> "Spiel beendet"
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = resultText, style = MaterialTheme.typography.displayMedium, color = Color.White)
            Spacer(modifier = Modifier.height(48.dp))
            Button(onClick = onBackToLobby) { Text("Zurück zur Lobby") }
        }
    }
}
