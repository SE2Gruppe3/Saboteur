package com.aau.saboteur.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    onStartGame: () -> Unit = {},
    onLeaveLobby: () -> Unit = {}
) {
    val lobbyState by viewModel.lobbyState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val playerId by viewModel.playerId.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val currentState = lobbyState
    val players = currentState?.players.orEmpty()
    val isHost = currentState?.hostId == playerId
    val playerCountError = playerCountError(players.size)

    HandleActiveLobbyEffects(
        currentState = currentState,
        playerId = playerId,
        username = username,
        onStartGame = onStartGame,
        onLeaveLobby = onLeaveLobby,
        setUsername = viewModel::setUsername
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Game Lobby",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(onClick = viewModel::leaveLobby) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Leave Lobby",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

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

            if (isSyncing) {
                SyncOverlay()
            }
        }
    }
}

@Composable
private fun SyncOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text("Synchronizing State...", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun HandleActiveLobbyEffects(
    currentState: LobbyState?,
    playerId: String?,
    username: String,
    onStartGame: () -> Unit,
    onLeaveLobby: () -> Unit,
    setUsername: (String) -> Unit
) {
    LaunchedEffect(currentState) {
        if (currentState == null) {
            onLeaveLobby()
        } else {
            setUsername(username)
        }
    }

    LaunchedEffect(currentState?.gameStarted, currentState?.players, playerId) {
        val state = currentState ?: return@LaunchedEffect
        val pid = playerId ?: return@LaunchedEffect
        
        // EDGE CASE FIX: Nur navigieren, wenn das Spiel gestartet ist UND wir Teil der Spielerliste sind.
        // Das verhindert, dass "Geister-Sessions" oder neue Logins in alte laufende Spiele springen.
        if (state.gameStarted) {
            val isParticipant = state.players.any { it.id == pid }
            if (isParticipant) {
                onStartGame()
            }
        }
    }
}

@Composable
private fun ActiveLobbyHeader(username: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Signed in as: $username",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoActiveLobbyMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No active lobby selected.",
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 20.dp, horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LOBBY CODE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                SelectionContainer {
                    Text(
                        text = state.lobbyCode,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 40.sp,
                            letterSpacing = 8.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Players",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Surface(
                color = MaterialTheme.colorScheme.secondary,
                shape = CircleShape
            ) {
                Text(
                    text = "${players.size} / $MAX_PLAYERS",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.SansSerif),
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
        }

        ActiveLobbyPlayerList(
            players = players,
            hostId = state.hostId,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            playerCountError?.let {
                ActiveLobbyMessage(
                    text = it,
                    icon = Icons.Default.Info,
                    color = MaterialTheme.colorScheme.error
                )
            }

            errorMessage?.let {
                ActiveLobbyMessage(
                    text = "Error: $it",
                    icon = Icons.Default.Info,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (isHost) {
            Button(
                onClick = onStartGameClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = playerCountError == null,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    "START GAME",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Waiting for $hostName to start...",
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActiveLobbyPlayerList(
    players: List<Player>,
    hostId: String,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(players) { player ->
            ActiveLobbyPlayerItem(
                player = player,
                isHost = player.id == hostId
            )
        }
    }
}

@Composable
private fun ActiveLobbyPlayerItem(
    player: Player,
    isHost: Boolean
) {
    val containerColor = if (isHost) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }

    val border = if (isHost) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    } else {
        null
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(8.dp),
        border = border
    ) {
        PlayerListItem(player = player, isHost = isHost)
    }
}

@Composable
private fun PlayerListItem(player: Player, isHost: Boolean) {
    ListItem(
        headlineContent = { PlayerHeadline(player.name, isHost) },
        supportingContent = if (isHost) {
            { HostSupportingContent() }
        } else null,
        leadingContent = { PlayerAvatar(player = player, isHost = isHost) },
        trailingContent = if (isHost) {
            { HostStarIcon(tint = MaterialTheme.colorScheme.primary) }
        } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun PlayerHeadline(name: String, isHost: Boolean) {
    val fontWeight = if (isHost) FontWeight.Bold else FontWeight.Normal
    val color = if (isHost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = name,
        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        fontWeight = fontWeight,
        color = color
    )
}

@Composable
private fun HostSupportingContent() {
    Text(
        text = "Lobby Host",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    )
}

@Composable
private fun PlayerAvatar(player: Player, isHost: Boolean) {
    val backgroundColor = if (isHost) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (isHost) {
            HostStarIcon(tint = MaterialTheme.colorScheme.onPrimary)
        } else {
            PlayerInitial(name = player.name)
        }
    }
}

@Composable
private fun PlayerInitial(name: String) {
    Text(
        text = name.take(1).uppercase(),
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.SansSerif),
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
private fun HostStarIcon(tint: Color) {
    Icon(
        imageVector = Icons.Default.Star,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(20.dp)
    )
}

@Composable
private fun ActiveLobbyMessage(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.SansSerif),
            fontWeight = FontWeight.Medium
        )
    }
}

private fun playerCountError(playerCount: Int): String? {
    return when {
        playerCount < MIN_PLAYERS -> "At least $MIN_PLAYERS players are required to start."
        playerCount > MAX_PLAYERS -> "Maximum $MAX_PLAYERS players allowed."
        else -> null
    }
}
