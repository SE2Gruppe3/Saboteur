package com.aau.saboteur.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
import com.aau.saboteur.ui.components.AvailableLobbies
import com.aau.saboteur.ui.components.LobbyMenu
import com.aau.saboteur.ui.components.MenuButton
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
    var menuOpen by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.8f) }

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
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Join a Game",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Box {
                    MenuButton(isOpen = menuOpen, onToggle = { menuOpen = !menuOpen })
                    LobbyMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        volume = volume,
                        onVolumeChange = { volume = it },
                        showLeaveGame = false
                    )
                }
            }

            LobbyHeader(username = username)

            ErrorMessage(errorMessage = errorMessage)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LobbyCodeInput(
                        lobbyCodeInput = lobbyCodeInput,
                        onLobbyCodeChange = { lobbyCodeInput = it }
                    )

                    LobbyActions(
                        username = username,
                        lobbyCodeInput = lobbyCodeInput,
                        onCreateLobby = viewModel::createLobby,
                        onJoinLobby = viewModel::joinLobby
                    )
                }
            }

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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorMessage(errorMessage: String?) {
    errorMessage?.let {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }
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
        label = { Text("Lobby Code") },
        placeholder = { Text("Enter code to join...") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onCreateLobby(username.trim()) },
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("CREATE", fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = { onJoinLobby(lobbyCodeInput.trim(), username.trim()) },
            enabled = lobbyCodeInput.isNotBlank(),
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("JOIN", fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LobbyDetails(currentState: LobbyState?) {
    currentState?.let { state ->
        val players = state.players
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Current Selection",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lobby: ${state.lobbyCode}",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Default,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "${players.size} Players",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 120.dp)
                    ) {
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
    }
}

@Composable
private fun LobbyPlayerItem(
    player: Player,
    isHost: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isHost) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isHost) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Text(
                    text = player.name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Text(
            text = player.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isHost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isHost) FontWeight.Bold else FontWeight.Normal
        )
    }
}
