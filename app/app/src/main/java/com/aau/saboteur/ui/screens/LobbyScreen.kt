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
    val availableLobbies by viewModel.availableLobbies.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Local input state (Name + Code)
    var playerName by remember { mutableStateOf("") }
    var lobbyCodeInput by remember { mutableStateOf("") }

    val currentState: LobbyState? = lobbyState
    val players: List<Player> = currentState?.players ?: emptyList()

    val hostName: String? = currentState
        ?.let { state -> state.players.firstOrNull { it.id == state.hostId }?.name }

    // Navigate to game if started
    LaunchedEffect(currentState?.gameStarted) {
        if (currentState?.gameStarted == true) {
            onGameStarted()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBrown)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "SABOTEUR - Lobby",
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
            label = { Text("Your Name") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Gold,
                unfocusedLabelColor = Color.Gray
            )
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = lobbyCodeInput,
            onValueChange = { lobbyCodeInput = it },
            label = { Text("Lobby Code (to join)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Gold,
                unfocusedLabelColor = Color.Gray
            )
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
                Text("CREATE", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.joinLobby(lobbyCodeInput.trim(), playerName.trim()) },
                enabled = playerName.isNotBlank() && lobbyCodeInput.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = FadedRed)
            ) {
                Text("JOIN", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Lobby info (Current)
        if (currentState != null) {
            Text(
                text = "Lobby Code: ${currentState.lobbyCode}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Host: ${hostName ?: "Unknown"}",
                fontSize = 14.sp,
                color = Gold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Online Spieler Section
            Text(
                text = "👨‍💻 PLAYERS IN LOBBY (${players.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Parchment)
            ) {
                LazyColumn(modifier = Modifier.padding(12.dp)) {
                    items(players) { player ->
                        val isHost = player.id == currentState.hostId
                        Text(
                            text = if (isHost) "${player.name} (Host) 👑" else player.name,
                            fontSize = 14.sp,
                            color = Color.Black,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Available Lobbies
        Text(
            text = "🎮 AVAILABLE LOBBIES",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Gold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (availableLobbies.isEmpty()) {
                item {
                    Text("No public lobbies found.", color = Color.Gray, fontSize = 12.sp)
                }
            }
            items(availableLobbies) { lobby ->
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
                                text = "Lobby ${lobby.lobbyCode}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = "${lobby.players.size} Players",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                        }

                        Button(
                            onClick = { lobbyCodeInput = lobby.lobbyCode },
                            colors = ButtonDefaults.buttonColors(containerColor = MossyGreen),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("SELECT", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Bottom Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.refreshLobbies() },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MossyGreen)
            ) {
                Text("REFRESH", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onBackPressed,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("BACK", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
