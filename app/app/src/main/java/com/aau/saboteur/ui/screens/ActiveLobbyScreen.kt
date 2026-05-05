package com.aau.saboteur.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
import com.aau.saboteur.viewModels.LobbyViewModel

private const val MIN_PLAYERS = 3
private const val MAX_PLAYERS = 10

@Composable
fun ActiveLobbyScreen(
    viewModel: LobbyViewModel,
    username: String,
    onStartGame: () -> Unit = {}
) {
    val lobbyState by viewModel.lobbyState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val playerId by viewModel.playerId.collectAsState()

    val currentState = lobbyState
    val players = currentState?.players.orEmpty()
    val isHost = currentState?.hostId == playerId
    val playerCountError = playerCountError(players.size)

    HandleActiveLobbyEffects(
        currentState = currentState,
        username = username,
        onStartGame = onStartGame,
        setCurrentPlayerId = viewModel::setCurrentPlayerId
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActiveLobbyHeader(username = username)

            currentState?.let { state ->
                ActiveLobbyContent(
                    state = state,
                    players = players,
                    errorMessage = errorMessage,
                    playerCountError = playerCountError,
                    isHost = isHost,
                    onStartGameClick = viewModel::startGame,
                    modifier = Modifier.weight(1f)
                )
            } ?: NoActiveLobbyMessage()
        }
    }
}

@Composable
private fun HandleActiveLobbyEffects(
    currentState: LobbyState?,
    username: String,
    onStartGame: () -> Unit,
    setCurrentPlayerId: (String) -> Unit
) {
    LaunchedEffect(currentState) {
        currentState?.let { setCurrentPlayerId(username) }
    }

    LaunchedEffect(currentState?.gameStarted) {
        if (currentState?.gameStarted == true) {
            onStartGame()
        }
    }
}

@Composable
private fun ActiveLobbyHeader(username: String) {
    Text(
        text = "Signed in as: $username",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    )
}

@Composable
private fun NoActiveLobbyMessage() {
    Text(
        text = "No active lobby selected.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun ActiveLobbyContent(
    state: LobbyState,
    players: List<Player>,
    errorMessage: String?,
    playerCountError: String?,
    isHost: Boolean,
    onStartGameClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hostName = state.players.firstOrNull { it.id == state.hostId }?.name ?: "Unknown"

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Lobby Code: ${state.lobbyCode}",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Host: $hostName",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Players (${players.size}/$MAX_PLAYERS)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        ActiveLobbyPlayerList(
            players = players,
            hostId = state.hostId,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        playerCountError?.let {
            ActiveLobbyMessage(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        errorMessage?.let {
            ActiveLobbyMessage(
                text = "Error: $it",
                color = MaterialTheme.colorScheme.error
            )
        }

        if (isHost) {
            HostStartGameButton(
                enabled = playerCountError == null,
                onClick = onStartGameClick
            )
        }
    }
}

@Composable
private fun ActiveLobbyPlayerList(
    players: List<Player>,
    hostId: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(players) { player ->
                ActiveLobbyPlayerItem(
                    player = player,
                    isHost = player.id == hostId
                )
            }
        }
    }
}

@Composable
private fun ActiveLobbyPlayerItem(
    player: Player,
    isHost: Boolean
) {
    Text(
        text = playerDisplayName(player, isHost),
        fontSize = 16.sp
    )
}

@Composable
private fun ActiveLobbyMessage(
    text: String,
    color: Color
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun HostStartGameButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Spacer(modifier = Modifier.height(4.dp))

    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text("START GAME", fontWeight = FontWeight.Bold)
    }
}

private fun playerDisplayName(player: Player, isHost: Boolean): String {
    return if (isHost) "${player.name} (Host) 👑" else player.name
}

private fun playerCountError(playerCount: Int): String? {
    return when {
        playerCount < MIN_PLAYERS -> "At least $MIN_PLAYERS players are required to start the game."
        playerCount > MAX_PLAYERS -> "A maximum of $MAX_PLAYERS players is allowed in a lobby."
        else -> null
    }
}
