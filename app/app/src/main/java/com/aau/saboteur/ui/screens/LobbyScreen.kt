package com.aau.saboteur.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
import com.aau.saboteur.ui.components.AvailableLobbies
import com.aau.saboteur.viewModels.LobbyViewModel

@Composable
fun LobbyScreen(
    viewModel: LobbyViewModel,
    username: String,
    onGameStarted: () -> Unit = {}
) {
    val lobbyState by viewModel.lobbyState.collectAsState()
    val availableLobbies by viewModel.availableLobbies.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var lobbyCodeInput by remember { mutableStateOf("") }

    val currentState: LobbyState? = lobbyState
    val players: List<Player> = currentState?.players ?: emptyList()

    val hostName: String? = currentState
        ?.let { state -> state.players.firstOrNull { it.id == state.hostId }?.name }

    LaunchedEffect(currentState?.gameStarted) {
        if (currentState?.gameStarted == true) {
            onGameStarted()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Signed in as: $username",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (errorMessage != null) {
                Text(
                    text = "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            OutlinedTextField(
                value = lobbyCodeInput,
                onValueChange = { lobbyCodeInput = it },
                label = { Text("Lobby Code (to join)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.createLobby(username.trim()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("CREATE", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.joinLobby(lobbyCodeInput.trim(), username.trim()) },
                    enabled = lobbyCodeInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text("JOIN", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (currentState != null) {
                Text(
                    text = "Lobby Code: ${currentState.lobbyCode}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Host: ${hostName ?: "Unknown"}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "👨‍💻 PLAYERS IN LOBBY (${players.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    LazyColumn(modifier = Modifier.padding(12.dp)) {
                        items(players) { player ->
                            val isHost = player.id == currentState.hostId
                            Text(
                                text = if (isHost) "${player.name} (Host) 👑" else player.name,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            AvailableLobbies(
                availableLobbies = availableLobbies,
                onLobbySelected = { lobbyCodeInput = it },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
