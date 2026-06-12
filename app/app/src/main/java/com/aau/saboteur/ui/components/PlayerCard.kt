package com.aau.saboteur.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.sp
import com.aau.saboteur.ui.theme.BoardGreen
import com.aau.saboteur.ui.theme.GlowGold
import com.aau.saboteur.ui.theme.GlowGreen
import com.aau.saboteur.ui.theme.MineCoal
import com.aau.saboteur.ui.theme.MineSlate
import com.aau.saboteur.ui.theme.OreCopper
import com.aau.saboteur.ui.theme.OreGold
import com.aau.saboteur.ui.theme.Quartz
import com.aau.saboteur.ui.theme.Steel
import com.aau.saboteur.model.PlayerTurn

@Composable
fun PlayerCard(
    player: PlayerTurn,
    isCurrentPlayer: Boolean,
    isLocalPlayer: Boolean = false,
    modifier: Modifier = Modifier
) {
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
    val borderColor = when {
        isCurrentPlayer -> GlowGold
        isLocalPlayer -> BoardGreen
        else -> Steel.copy(alpha = 0.45f)
    }
    val shadowColor = when {
        isCurrentPlayer -> GlowGold
        isLocalPlayer -> GlowGreen
        else -> Color.Black.copy(alpha = 0.2f)
    }
    val borderWidth = if (isCurrentPlayer || isLocalPlayer) 2.5.dp else 1.dp
    val shadowElevation = if (isCurrentPlayer || isLocalPlayer) 16.dp else 8.dp

    Card(
        modifier = modifier.then(
            Modifier.shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
        ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = borderWidth,
            color = borderColor
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = player.playerName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCurrentPlayer) MineCoal else Quartz
                    )
                    if (player.blockedTools.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            player.blockedTools.forEach { tool ->
                                val emoji = when (tool.name) {
                                    "PICKAXE" -> "⛏️"
                                    "LANTERN" -> "🏮"
                                    "CART" -> "🛒"
                                    else -> ""
                                }
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .border(2.dp, Color.Red, MaterialTheme.shapes.small),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}