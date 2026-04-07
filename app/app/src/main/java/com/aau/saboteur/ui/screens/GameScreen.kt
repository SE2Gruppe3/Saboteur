package com.aau.saboteur.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aau.saboteur.model.PlayerTurn
import com.aau.saboteur.ui.TunnelCardView
import com.aau.saboteur.ui.components.PlayerTurnOrderRow
import com.aau.saboteur.ui.components.RoleCardView
import com.aau.saboteur.viewModels.GameViewModel

@Composable
fun GameScreen(
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sortedPlayers = uiState.gameState.players.sortedBy(PlayerTurn::turnOrder)
    val currentHand = uiState.gameState.currentPlayerId?.let { uiState.hands?.get(it) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (sortedPlayers.isNotEmpty()) {
                PlayerTurnOrderRow(
                    players = sortedPlayers,
                    currentPlayerId = uiState.gameState.currentPlayerId
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = viewModel::startGame
                ) {
                    Text(if (uiState.isStartingGame) "Starting game..." else "Start Game")
                }

                if (sortedPlayers.isEmpty()) {
                    Text(
                        text = "No game state yet. Press Start Game.",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }

                uiState.errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }

        // Role Card in the bottom left corner
        uiState.player?.role?.let { role ->
            RoleCardView(
                role = role,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        if (currentHand != null) {
            Text(
                text = "${sortedPlayers.firstOrNull { it.playerId == uiState.gameState.currentPlayerId }?.playerName}'s Hand",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                items(currentHand) { card ->
                    TunnelCardView(card = card)
                }
            }
        }
    }
}