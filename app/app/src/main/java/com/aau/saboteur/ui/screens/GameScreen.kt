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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aau.saboteur.model.*
import com.aau.saboteur.ui.components.*
import com.aau.saboteur.viewModels.GameViewModel
import com.aau.saboteur.viewModels.LobbyViewModel
import kotlinx.coroutines.delay

private fun isBlockCard(type: CardType) =
    type == CardType.CART_RED || type == CardType.LANTERN_RED || type == CardType.PICKAXE_RED

private fun isRepairCard(type: CardType) =
    type == CardType.CART_GREEN || type == CardType.LANTERN_GREEN || type == CardType.PICKAXE_GREEN ||
            type == CardType.DOUBLE_LANTERN_CART || type == CardType.DOUBLE_PICKAXE_CART || type == CardType.DOUBLE_PICKAXE_LANTERN

private fun needsTargetDialog(type: CardType) = isBlockCard(type) || isRepairCard(type)

@Composable
fun GameScreen(
    lobbyViewModel: LobbyViewModel,
    onBackToLobby: () -> Unit = {},
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val localPlayerId by lobbyViewModel.playerId.collectAsState(initial = null)
    val lobbyState by lobbyViewModel.lobbyState.collectAsState()
    val lobbyCode = lobbyState?.lobbyCode
    val validPositions by viewModel.validPositions.collectAsState()

    var gameOverWinner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.gameOverEvents.collect { winner -> gameOverWinner = winner }
    }

    LaunchedEffect(localPlayerId, lobbyCode) {
        if (localPlayerId != null && lobbyCode != null) {
            viewModel.initGameSession(lobbyCode!!, localPlayerId!!)
        }
    }

    val sortedPlayers = uiState.gameState.players
    val currentHand = uiState.localPlayerId?.let { uiState.hands?.get(it) }
    val isMyTurn = uiState.localPlayerId != null &&
            uiState.gameState.currentPlayerId == uiState.localPlayerId &&
            !uiState.isSyncing

    var showBlockDialog by remember { mutableStateOf(false) }

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
                    onCardSelected = { card ->
                        if (isMyTurn) {
                            viewModel.selectCard(card)
                            if (needsTargetDialog(card.type)) showBlockDialog = true
                        }
                    },
                    onCardRotated = { card, rotated -> viewModel.onCardRotated(card, rotated) }
                )
            }
        }

        gameOverWinner?.let { winner ->
            GameOverDialog(winner = winner, onBackToLobby = onBackToLobby)
        }

        if (uiState.isSyncing) {
            GameSyncOverlay()
        }

        val selected = uiState.selectedCard
        if (selected != null && needsTargetDialog(selected.type) && showBlockDialog) {
            BlockTargetDialog(
                playerList = uiState.gameState.players,
                selfPlayerId = uiState.localPlayerId ?: "",
                onPlayerSelected = { targetId ->
                    if (isBlockCard(selected.type)) {
                        viewModel.playBlockCardOnPlayer(targetId)
                    } else if (isRepairCard(selected.type)) {
                        val tool = when (selected.type) {
                            CardType.LANTERN_GREEN -> "LANTERN"
                            CardType.PICKAXE_GREEN -> "PICKAXE"
                            CardType.CART_GREEN -> "CART"
                            CardType.DOUBLE_LANTERN_CART -> "LANTERN"
                            CardType.DOUBLE_PICKAXE_CART -> "PICKAXE"
                            CardType.DOUBLE_PICKAXE_LANTERN -> "PICKAXE"
                            else -> "LANTERN"
                        }
                        viewModel.playRepairCardOnPlayer(targetId, tool)
                    }
                    showBlockDialog = false
                },
                onDismiss = { showBlockDialog = false }
            )
        }
    }
}

@Composable
fun BlockTargetDialog(
    playerList: List<PlayerTurn>,
    selfPlayerId: String,
    onPlayerSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wähle einen Spieler") },
        text = {
            Column {
                playerList
                    .filter { it.playerId != selfPlayerId }
                    .forEach { player ->
                        Button(
                            onClick = { onPlayerSelected(player.playerId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(player.playerName)
                        }
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
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
                Text("Synchronisierung...", style = MaterialTheme.typography.headlineSmall)
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = resultText, style = MaterialTheme.typography.displayMedium, color = Color.White)
            Spacer(modifier = Modifier.height(48.dp))
            Button(onClick = onBackToLobby) { Text("Zurück zur Lobby") }
        }
    }
}