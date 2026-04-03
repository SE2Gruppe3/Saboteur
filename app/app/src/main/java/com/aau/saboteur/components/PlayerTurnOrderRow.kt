package com.aau.saboteur.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aau.saboteur.ui.theme.GlowGold
import com.aau.saboteur.ui.theme.MineCoal
import com.aau.saboteur.ui.theme.MineSlate
import com.aau.saboteur.ui.theme.OreCopper
import com.aau.saboteur.ui.theme.OreGold
import com.aau.saboteur.ui.theme.Quartz
import com.aau.saboteur.ui.theme.Steel
import com.aau.shared.game.PlayerTurn

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
            val isCurrentPlayer = player.playerId == currentPlayerId
            val shape = RoundedCornerShape(20.dp)
            val cardBrush = if (isCurrentPlayer) {
                Brush.linearGradient(
                    colors = listOf(Quartz, OreGold, OreCopper)
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(MineCoal, MineSlate)
                )
            }

            Card(
                modifier = if (isCurrentPlayer) {
                    Modifier.shadow(
                        elevation = 16.dp,
                        shape = shape,
                        ambientColor = GlowGold,
                        spotColor = GlowGold
                    )
                } else {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.2f),
                        spotColor = Color.Black.copy(alpha = 0.2f)
                    )
                },
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(
                    width = if (isCurrentPlayer) 2.5.dp else 1.dp,
                    color = if (isCurrentPlayer) GlowGold else Steel.copy(alpha = 0.45f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(cardBrush)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = player.playerName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCurrentPlayer) MineCoal else Quartz
                        )
                    }
                }
            }
        }
    }
}
