package com.aau.saboteur.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
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
    val lobbyState by viewModel.lobbyState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Local input state (Name + Code)
    var playerName by remember { mutableStateOf("") }
    var lobbyCode by remember { mutableStateOf("") }

    // Convenience: if null, use empty placeholders
    val currentState: LobbyState? = lobbyState
    val players: List<Player> = currentState?.players ?: emptyList()

    val hostName: String? = currentState
        ?.let { state -> state.players.firstOrNull { it.id == state.hostId }?.name }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBrown)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "SABOTEUR - LobbyScreen",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Gold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x33FF0000))
            ) {
                Text(
                    text = "Error: $errorMessage",
                    color = Color.White,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Inputs
        OutlinedTextField(
            value = playerName,
            onValueChange = { playerName = it },
            label = { Text("Player name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = lobbyCode,
            onValueChange = { lobbyCode = it },
            label = { Text("Lobby code (zum Joinen)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Quick actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.createLobby(playerName.trim()) },
                enabled = playerName.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Gold)
            ) {
                Text(
                    "CREATE",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { viewModel.joinLobby(lobbyCode.trim(), playerName.trim()) },
                enabled = playerName.isNotBlank() && lobbyCode.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = FadedRed)
            ) {
                Text("JOIN", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Lobby info (only if we have a state from server)
        if (currentState != null) {
            Text(
                text = "Lobby Code: ${currentState.lobbyCode}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Host: ${hostName ?: "Unbekannt"}",
                fontSize = 14.sp,
                color = Gold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        } else {
            Text(
                text = "Noch keine Lobby – erst CREATE oder JOIN drücken.",
                fontSize = 14.sp,
                color = Gold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Online Spieler Section
        Text(
            text = "👨‍💻 ONLINE SPIELER",
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
            if (players.isEmpty()) {
                Text(
                    text = "Keine Spieler vorhanden.",
                    fontSize = 14.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(12.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.padding(12.dp)) {
                    items(players) { player ->
                        val isHost = currentState != null && player.id == currentState.hostId

                        Text(
                            text = buildString {
                                append(player.name)
                                if (isHost) append(" (Host)")
                            },
                            fontSize = 14.sp,
                            color = Color.Black,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Verfügbare Lobbys Section (noch Dummy)
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
                                // weiterhin Dummy: füllt nur LobbyCode in Textfield,
                                // damit du schnell JOIN drücken kannst
                                lobbyCode = currentState?.lobbyCode ?: lobbyCode
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MossyGreen)
                        ) {
                            Text("SELECT", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Action Buttons unten
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { /* später: Lobby-Liste vom Backend laden */ },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MossyGreen)
            ) {
                Text("REFRESH", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onBackPressed,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("BACK", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LobbyScreenPreview() {
    Text("LobbyScreen Preview disabled (requires server-backed ViewModel)")
}