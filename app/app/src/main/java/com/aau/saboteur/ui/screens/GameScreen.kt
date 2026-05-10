package com.aau.saboteur.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aau.saboteur.model.PlayerTurn
import com.aau.saboteur.ui.components.BoardGrid
import com.aau.saboteur.ui.components.PlayerHandRow
import com.aau.saboteur.ui.components.PlayerTurnOrderRow
import com.aau.saboteur.ui.components.RoleCardView
import com.aau.saboteur.viewModels.GameViewModel
import com.aau.saboteur.viewModels.LobbyViewModel

@Composable
fun GameScreen(
    lobbyViewModel: LobbyViewModel,
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val localPlayerId by lobbyViewModel.playerId.collectAsState()
    val sortedPlayers = uiState.gameState.players.sortedBy(PlayerTurn::turnOrder)
    val currentHand = uiState.localPlayerId?.let { uiState.hands?.get(it) }
    val isMyTurn = uiState.localPlayerId != null &&
            uiState.gameState.currentPlayerId == uiState.localPlayerId

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
            onCellClick = { position ->
                if (isMyTurn && uiState.selectedCard != null) {
                    viewModel.onBoardCellClicked(position)
                }
            }
        )

        // Top: turn order
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
                    .padding(top = 16.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter)
            ) {
                PlayerTurnOrderRow(
                    players = sortedPlayers,
                    currentPlayerId = uiState.gameState.currentPlayerId
                )
            }
        }

        // Center: status / errors
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sortedPlayers.isEmpty()) {
                Text(
                    text = if (uiState.isStartingGame) "Starting game..." else "Waiting for game state...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (isMyTurn && sortedPlayers.isNotEmpty()) {
                val hintText = if (uiState.selectedCard != null)
                    "Tap a board cell to place – or discard below"
                else if (showTurnHint)
                    "Your turn! Tap a card to select"
                else null

                hintText?.let {
                    Surface(
                        color = Color(0xFF6E5524).copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 4.dp
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

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

        // Bottom: role card + optional discard + hand
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
                    RoleCardView(
                        role = role,
                        compact = true,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                if (isMyTurn && uiState.selectedCard != null) {
                    Button(
                        onClick = { viewModel.discardSelectedCard() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Discard \"${uiState.selectedCard!!.type.name}\"")
                    }
                }

                PlayerHandRow(
                    hand = currentHand,
                    selectedCardId = uiState.selectedCard?.id,
                    onCardSelected = { card ->
                        if (isMyTurn) viewModel.selectCard(card)
                    },
                    onCardRotated = { card, rotated -> viewModel.onCardRotated(card, rotated) }
                )
            }
        }
    }
}