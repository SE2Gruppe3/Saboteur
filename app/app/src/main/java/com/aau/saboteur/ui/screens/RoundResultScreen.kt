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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aau.saboteur.R
import com.aau.saboteur.model.PlayerTurn
import com.aau.saboteur.model.Role
import com.aau.saboteur.model.RoundResult
import com.aau.saboteur.ui.theme.OreGold
import com.aau.saboteur.util.LanguageManager
import com.aau.saboteur.util.rememberLocalizedContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue

@Composable
fun RoundResultScreen(
    roundResult: RoundResult,
    players: List<PlayerTurn>,
    onNextRound: () -> Unit
) {
    val language by LanguageManager.currentLanguage
    val localizedContext = rememberLocalizedContext(language)

    CompositionLocalProvider(LocalContext provides localizedContext) {
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
                        text = stringResource(R.string.round_result_title, roundResult.roundNumber),
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
                        stringResource(R.string.round_result_winner_golddiggers) else stringResource(R.string.round_result_winner_saboteurs)

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
                            text = stringResource(R.string.round_result_roles_title),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        players.forEach { player ->
                            val revealedRole = roundResult.revealedRoles[player.playerId]
                            val roleIcon = when (revealedRole) {
                                Role.GOLDDIGGER -> "⛏️"
                                Role.SABOTEUR -> "🔴"
                                null -> "❓"
                            }
                            val roleText = when (revealedRole) {
                                Role.GOLDDIGGER -> stringResource(R.string.role_golddigger_label)
                                Role.SABOTEUR -> stringResource(R.string.role_saboteur_label)
                                null -> stringResource(R.string.role_unknown_label)
                            }
                            val roleColor = when (revealedRole) {
                                Role.GOLDDIGGER -> OreGold
                                Role.SABOTEUR -> Color(0xFFD32F2F)
                                null -> Color.Gray
                            }

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
                            text = stringResource(R.string.next_round_button_label),
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
}
