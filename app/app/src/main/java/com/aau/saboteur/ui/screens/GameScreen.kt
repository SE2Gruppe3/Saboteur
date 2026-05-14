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
            validPositions = validPositions,
            onCellClick = { position ->
                if (isMyTurn && uiState.selectedCard != null) {
                    viewModel.onBoardCellClicked(position)
                }
            }
        )

        // Top: turn order + placement hint
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

            if (isMyTurn && sortedPlayers.isNotEmpty()) {
                val hintText = when {
                    uiState.selectedCard != null -> "Tippe auf ein Feld zum Platzieren – oder verwirf die Karte unten."
                    showTurnHint -> stringResource(R.string.your_turn_hint)
                    else -> null
                }
                hintText?.let {
                    Surface(
                        color = Color(0xFF6E5524).copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 4.dp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
        }

        // Center: waiting text + errors
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sortedPlayers.isEmpty()) {
                Text(
                    text = if (uiState.isStartingGame) stringResource(R.string.starting_game) else stringResource(R.string.waiting_for_game_state),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
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
                        Text("Karte verwerfen")
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

        gameOverWinner?.let { winner ->
            val resultText = when (winner) {
                "DWARVES" -> "Zwerge gewinnen! ⛏️"
                "SABOTEURS" -> "Saboteure gewinnen! 🪓"
                else -> "Spiel beendet"
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(48.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = resultText,
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onBackToLobby) {
                        Text("Zurück zur Lobby")
                    }
                }
            }
        }
    }
}
