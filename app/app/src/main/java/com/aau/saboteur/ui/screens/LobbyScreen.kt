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
    onLobbyJoined: () -> Unit = {},
    onGameStarted: () -> Unit = {}
) {
    val lobbyState by viewModel.lobbyState.collectAsState()
    val availableLobbies by viewModel.availableLobbies.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var lobbyCodeInput by remember { mutableStateOf("") }

    HandleLobbyNavigation(
        currentState = lobbyState,
        onLobbyJoined = onLobbyJoined,
        onGameStarted = onGameStarted
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LobbyHeader(username = username)
            ErrorMessage(errorMessage = errorMessage)
            LobbyCodeInput(
                lobbyCodeInput = lobbyCodeInput,
                onLobbyCodeChange = { lobbyCodeInput = it }
            )

            Spacer(Modifier.height(12.dp))

            LobbyActions(
                username = username,
                lobbyCodeInput = lobbyCodeInput,
                onCreateLobby = viewModel::createLobby,
                onJoinLobby = viewModel::joinLobby
            )

            Spacer(Modifier.height(16.dp))

            LobbyDetails(currentState = lobbyState)

            AvailableLobbies(
                availableLobbies = availableLobbies,
                onLobbySelected = { lobbyCodeInput = it },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HandleLobbyNavigation(
    currentState: LobbyState?,
    onLobbyJoined: () -> Unit,
    onGameStarted: () -> Unit
) {
    LaunchedEffect(currentState?.lobbyCode) {
        currentState?.let { onLobbyJoined() }
    }

    LaunchedEffect(currentState?.gameStarted) {
        if (currentState?.gameStarted == true) {
            onGameStarted()
        }
    }
}

@Composable
private fun LobbyHeader(username: String) {
    Text(
        text = "Signed in as: $username",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
private fun ErrorMessage(errorMessage: String?) {
    errorMessage?.let {
        Text(
            text = "Error: $it",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun LobbyCodeInput(
    lobbyCodeInput: String,
    onLobbyCodeChange: (String) -> Unit
) {
    OutlinedTextField(
        value = lobbyCodeInput,
        onValueChange = onLobbyCodeChange,
        label = { Text("Lobby Code (to join)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun LobbyActions(
    username: String,
    lobbyCodeInput: String,
    onCreateLobby: (String) -> Unit,
    onJoinLobby: (String, String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { onCreateLobby(username.trim()) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("CREATE", fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = { onJoinLobby(lobbyCodeInput.trim(), username.trim()) },
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
}

@Composable
private fun LobbyDetails(currentState: LobbyState?) {
    currentState?.let { state ->
        val players = state.players
        val hostName = state.players.firstOrNull { it.id == state.hostId }?.name ?: "Unknown"

        Text(
            text = "Lobby Code: ${state.lobbyCode}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Host: $hostName",
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
                    LobbyPlayerItem(
                        player = player,
                        isHost = player.id == state.hostId
                    )
                }
            }
        }
    }
}

@Composable
private fun LobbyPlayerItem(
    player: Player,
    isHost: Boolean
) {
    Text(
        text = playerDisplayName(player = player, isHost = isHost),
        fontSize = 14.sp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

private fun playerDisplayName(player: Player, isHost: Boolean): String {
    return if (isHost) "${player.name} (Host) 👑" else player.name
}
