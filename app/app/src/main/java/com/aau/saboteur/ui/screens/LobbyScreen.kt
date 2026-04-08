package com.aau.saboteur.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aau.saboteur.ui.theme.DarkBrown
import com.aau.saboteur.ui.theme.FadedRed
import com.aau.saboteur.ui.theme.Gold
import com.aau.saboteur.ui.theme.MossyGreen
import com.aau.saboteur.ui.theme.Parchment
import com.aau.saboteur.viewModels.LobbyViewModel

@Composable
fun LobbyScreen(
    viewModel: LobbyViewModel,
    onBackPressed: () -> Unit = {},
    onGameStarted: () -> Unit = {}
) {
    val lobbyState = viewModel.lobbyState.collectAsState()
    val currentState = lobbyState.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBrown)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "SABOTEUR - HAUPTBILDSCHIRM",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Gold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        // Online Spieler Section
        Text(
            text = "📊 ONLINE SPIELER",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Gold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Parchment)
        ) {
            LazyColumn(
                modifier = Modifier.padding(12.dp)
            ) {
                items(currentState.players) { player ->
                    Text(
                        text = player.name,
                        fontSize = 14.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        // Verfügbare Lobbys Section
        Text(
            text = "🎮 VERFÜGBARE LOBBYS",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Gold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            items(3) { index ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Parchment)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Lobby ${index + 1}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = "${index + 2}/${10} Spieler",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.joinLobby("${1000 + index}", "Player${currentState.players.size + 1}")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FadedRed)
                        ) {
                            Text("JOIN", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    viewModel.createLobby("Player${(1000..9999).random()}")
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold)
            ) {
                Text(
                    "CREATE NEW LOBBY",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Button(
                onClick = {
                    // REFRESH - lädt Lobbys neu (später mit Backend)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MossyGreen)
            ) {
                Text("REFRESH", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LobbyScreenPreview() {
    val testViewModel = LobbyViewModel().apply {
        createLobby("Sebastian")
        joinLobby("1234", "Player2")
        joinLobby("1234", "Player3")
    }

    LobbyScreen(
        viewModel = testViewModel,
        onBackPressed = { },
        onGameStarted = { }
    )
}