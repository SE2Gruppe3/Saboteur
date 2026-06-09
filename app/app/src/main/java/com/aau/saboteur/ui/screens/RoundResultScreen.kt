package com.aau.saboteur.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aau.saboteur.model.PlayerTurn
import com.aau.saboteur.model.Role
import com.aau.saboteur.model.RoundResult
import com.aau.saboteur.ui.theme.OreGold

@Composable
fun RoundResultScreen(
    roundResult: RoundResult,
    players: List<PlayerTurn>,
    onNextRound: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(2.dp, OreGold),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Text(
                    text = "Runde ${roundResult.roundNumber} beendet",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )


                val winnerColor = if (roundResult.winnerRole == Role.GOLDDIGGER)
                    OreGold else Color(0xFFD32F2F)
                val winnerIcon = if (roundResult.winnerRole == Role.GOLDDIGGER) "⛏️" else "🔴"
                val winnerText = if (roundResult.winnerRole == Role.GOLDDIGGER)
                    "Goldsucher gewinnen" else "Saboteure gewinnen"

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = winnerColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, winnerColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(winnerIcon, fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = winnerText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            ),
                            color = winnerColor
                        )
                    }
                }


                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Rollen dieser Runde",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    players.forEach { player ->
                        val revealedRole = roundResult.revealedRoles[player.playerId]
                        val roleIcon = if (revealedRole == Role.GOLDDIGGER) "⛏️" else "🔴"
                        val roleText = if (revealedRole == Role.GOLDDIGGER) "Goldsucher" else "Saboteur"
                        val roleColor = if (revealedRole == Role.GOLDDIGGER)
                            OreGold else Color(0xFFD32F2F)

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(roleIcon, fontSize = 18.sp)
                                    Text(
                                        text = player.playerName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.SansSerif
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = roleText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif
                                    ),
                                    color = roleColor
                                )
                            }
                        }
                    }
                }


                Button(
                    onClick = onNextRound,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        "Weiter zur nächsten Runde",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    )
                }
            }
        }
    }
}