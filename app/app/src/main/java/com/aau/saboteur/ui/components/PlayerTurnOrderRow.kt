package com.aau.saboteur.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aau.saboteur.model.PlayerTurn

@Composable
fun PlayerTurnOrderRow(
    players: List<PlayerTurn>,
    currentPlayerId: String?
) {
    Row(
        modifier = Modifier
            .padding(top = 16.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        players.forEach { player ->
            PlayerCard(
                player = player,
                isCurrentPlayer = player.playerId == currentPlayerId
            )
        }
    }
}
