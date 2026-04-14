package com.aau.saboteur.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.aau.saboteur.mockeddata.boardStartPosition
import com.aau.saboteur.mockeddata.mockBoardPlacements
import com.aau.saboteur.ui.components.BoardGrid
import com.aau.saboteur.ui.components.PlayerHandRow
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (sortedPlayers.isNotEmpty()) {
                PlayerTurnOrderRow(
                    players = sortedPlayers,
                    currentPlayerId = uiState.gameState.currentPlayerId
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BoardGrid(
                    placements = mockBoardPlacements,
                    startPosition = boardStartPosition
                )

                Button(
                    onClick = viewModel::startGame
                ) {
                    Text(if (uiState.isStartingGame) "Starting game..." else "Start Game")
                }

                if (sortedPlayers.isEmpty()) {
                    Text(
                        text = "Grundraster aktiv. Press Start Game for player state.",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                uiState.errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        if (currentHand != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                uiState.player?.role?.let { role ->
                    RoleCardView(
                        role = role,
                        compact = true,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                PlayerHandRow(
                    hand = currentHand
                )
            }
        }
    }
}
