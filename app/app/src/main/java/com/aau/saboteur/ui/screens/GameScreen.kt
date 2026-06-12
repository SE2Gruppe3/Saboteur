package com.aau.saboteur.ui.screens

import android.content.Context
import android.hardware.camera2.CameraManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aau.saboteur.R
import com.aau.saboteur.model.*
import com.aau.saboteur.sound.GameAudio
import com.aau.saboteur.ui.components.*
import com.aau.saboteur.viewModels.GameViewModel
import com.aau.saboteur.viewModels.LobbyViewModel
import androidx.compose.foundation.BorderStroke

private val DeckBadgeBackground = Color(0xFF2A2A2A)
private val DeckBadgeIconBackground = Color(0xFF1A1A1A)
private val DeckBadgeShape = RoundedCornerShape(12.dp)
private val DeckBadgeIconShape = RoundedCornerShape(2.dp)
private const val DeckBadgeBorderAlpha = 0.4f
private const val DeckBadgeIconBorderAlpha = 0.6f
private val DeckBadgeIconWidth = 10.dp
private val DeckBadgeIconHeight = 14.dp
private val DeckBadgePaddingH = 12.dp
private val DeckBadgePaddingV = 6.dp
private val DeckBadgeIconSpacing = 6.dp

private fun isToolBlocked(blockedTools: Set<ToolType>, tool: String): Boolean {
    return blockedTools.any { it.name == tool }
}

private fun isBlockCard(type: CardType) =
    type == CardType.CART_RED || type == CardType.LANTERN_RED || type == CardType.PICKAXE_RED

private fun isRepairCard(type: CardType) =
    type == CardType.CART_GREEN || type == CardType.LANTERN_GREEN || type == CardType.PICKAXE_GREEN
            || type == CardType.DOUBLE_LANTERN_CART || type == CardType.DOUBLE_PICKAXE_CART || type == CardType.DOUBLE_PICKAXE_LANTERN

private fun needsTargetDialog(type: CardType) = isBlockCard(type) || isRepairCard(type)

private fun getRepairToolsFromCard(type: CardType): List<String> = when(type) {
    CardType.LANTERN_GREEN -> listOf("LANTERN")
    CardType.PICKAXE_GREEN -> listOf("PICKAXE")
    CardType.CART_GREEN -> listOf("CART")
    CardType.DOUBLE_LANTERN_CART -> listOf("LANTERN", "CART")
    CardType.DOUBLE_PICKAXE_CART -> listOf("PICKAXE", "CART")
    CardType.DOUBLE_PICKAXE_LANTERN -> listOf("PICKAXE", "LANTERN")
    else -> emptyList()
}

@Composable
private fun ToolIcons(blockedTools: Set<ToolType>) {
    if (blockedTools.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            blockedTools.forEach { tool ->
                val emoji = when (tool.name) {
                    "PICKAXE" -> "⛏️"
                    "LANTERN" -> "🏮"
                    "CART" -> "🛒"
                    else -> ""
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(2.dp, Color.Red, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

@Composable
fun GameScreen(
    lobbyViewModel: LobbyViewModel,
    onBackToLobby: () -> Unit = {},
    onBackToActiveLobby: () -> Unit = {},
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val localPlayerId by lobbyViewModel.playerId.collectAsState(initial = null)
    val lobbyState by lobbyViewModel.lobbyState.collectAsState()
    val lobbyCode = lobbyState?.lobbyCode
    val validPositions by viewModel.validPositions.collectAsState()
    var showRoundResult by remember { mutableStateOf(false) }
    var showFinalResult by remember { mutableStateOf(false) }



    LaunchedEffect(Unit) {
        viewModel.roundResultScreenRequested.collect {
            showRoundResult = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.finalResultScreenRequested.collect {
            showFinalResult = true
        }
    }

    LaunchedEffect(localPlayerId, lobbyCode) {
        if (localPlayerId != null && lobbyCode != null) {
            viewModel.initGameSession(lobbyCode, localPlayerId!!)
        }
    }

    val sortedPlayers = uiState.gameState.players
    val currentHand = uiState.localPlayerId?.let { uiState.hands?.get(it) }
    val isMyTurn = uiState.localPlayerId != null &&
            uiState.gameState.currentPlayerId == uiState.localPlayerId &&
            !uiState.isSyncing

    val context = LocalContext.current
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val currentIsMyTurn by rememberUpdatedState(isMyTurn)
    DisposableEffect(Unit) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (enabled && currentIsMyTurn) {
                    viewModel.triggerCheat(CheatType.LANTERN_FLASHLIGHT)
                }
            }
        }
        cameraManager.registerTorchCallback(callback, null)
        onDispose {
            cameraManager.unregisterTorchCallback(callback)
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var showSpielregeln by remember { mutableStateOf(false) }

    GameAudio(
        gameState = uiState.gameState,
        mapResult = uiState.lastMapResult,
        volume = volume,
        enabled = !uiState.gameState.isGameOver
    )

    var showBlockDialog by remember { mutableStateOf(false) }
    var showToolDialog by remember { mutableStateOf(false) }
    var pendingToolSelection by remember { mutableStateOf<Pair<String, List<String>>?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        uiState.lastMapResult?.let { result ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissMapResult() },
                title = { Text(stringResource(R.string.secret_info_title)) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.peek_goal_card_message))
                        Spacer(modifier = Modifier.height(12.dp))
                        val cardName = if (result.card.isGoal) stringResource(R.string.gold_found) else stringResource(R.string.only_stone)
                        Text(cardName, style = MaterialTheme.typography.headlineMedium)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissMapResult() }) { Text(stringResource(R.string.ok_button)) }
                }
            )
        }

        BoardGrid(
            placements = uiState.gameState.boardPlacements,
            modifier = Modifier.fillMaxSize(),
            validPositions = validPositions,
            onCellClick = { position ->
                if (isMyTurn && uiState.selectedCard != null) {
                    viewModel.onBoardCellClicked(position)
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (sortedPlayers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        PlayerTurnOrderRow(
                            players = sortedPlayers,
                            currentPlayerId = uiState.gameState.currentPlayerId,
                            localPlayerId = uiState.localPlayerId
                        )
                    }
                    Box {
                        MenuButton(isOpen = menuOpen, onToggle = { menuOpen = !menuOpen })
                        LobbyMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            volume = volume,
                            onVolumeChange = { volume = it },
                            onLeaveGame = onBackToLobby,
                            onShowSpielregeln = { showSpielregeln = true }
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            uiState.errorMessage?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = localizeServerError(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
        if (currentHand != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(bottom = 24.dp, top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    uiState.player?.role?.let { role ->
                        RoleCardView(role = role, compact = true)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    DeckBadge(count = uiState.remainingDeckSize)
                }
                if (isMyTurn && uiState.selectedCard != null) {
                    Button(
                        onClick = { viewModel.discardSelectedCard() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(stringResource(R.string.discard_card_button), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                PlayerHandRow(
                    modifier = Modifier.fillMaxWidth(),
                    hand = currentHand,
                    selectedCardId = uiState.selectedCard?.id,
                    onCardSelected = { card ->
                        if (isMyTurn) {
                            viewModel.selectCard(card)
                            if (needsTargetDialog(card.type)) showBlockDialog = true
                        }
                    },
                    onCardRotated = { card, rotated -> viewModel.onCardRotated(card, rotated) }
                )
            }
        }

        if (uiState.isSyncing) {
            GameSyncOverlay()
        }

        val selected = uiState.selectedCard
        if (selected != null && needsTargetDialog(selected.type) && showBlockDialog) {
            BlockTargetDialog(
                playerList = uiState.gameState.players,
                selfPlayerId = uiState.localPlayerId ?: "",
                onPlayerSelected = { targetId ->
                    if (isBlockCard(selected.type)) {
                        viewModel.playBlockCardOnPlayer(targetId)
                        showBlockDialog = false
                    } else if (isRepairCard(selected.type)) {
                        val repairTools = getRepairToolsFromCard(selected.type)
                        val player = uiState.gameState.players.find { it.playerId == targetId }
                        if (player != null) {
                            val blocked = repairTools.filter { tool -> isToolBlocked(player.blockedTools, tool) }
                            when (blocked.size) {
                                0 -> {
                                    viewModel.playRepairCardOnPlayer(targetId, repairTools.first())
                                    showBlockDialog = false
                                }
                                1 -> {
                                    viewModel.playRepairCardOnPlayer(targetId, blocked.first())
                                    showBlockDialog = false
                                }
                                else -> {
                                    pendingToolSelection = Pair(targetId, blocked)
                                    showToolDialog = true
                                    showBlockDialog = false
                                }
                            }
                        } else {
                            showBlockDialog = false
                        }
                    }
                },
                onDismiss = { showBlockDialog = false }
            )
        }
        if (showToolDialog && pendingToolSelection != null) {
            val (targetId, tools) = pendingToolSelection!!
            DoubleRepairToolDialog(
                tools = tools,
                onToolSelected = { tool ->
                    viewModel.playRepairCardOnPlayer(targetId, tool)
                    showToolDialog = false
                    pendingToolSelection = null
                },
                onDismiss = {
                    showToolDialog = false
                    pendingToolSelection = null
                }
            )
        }

        if (showSpielregeln) {
            SpielregelnDialog(onDismiss = { showSpielregeln = false })
        }

        if (showRoundResult && uiState.gameState.lastRoundResult != null) {
            RoundResultScreen(
                roundResult = uiState.gameState.lastRoundResult!!,
                players = uiState.gameState.players,
                onNextRound = {
                    showRoundResult = false
                }
            )
        }

        if (showFinalResult && uiState.gameState.lastRoundResult != null) {
            FinalResultScreen(
                roundResult = uiState.gameState.lastRoundResult!!,
                players = uiState.gameState.players,
                onBackToLobby = {
                    showFinalResult = false
                    onBackToActiveLobby()
                }
            )
        }
    }
}

@Composable
private fun DeckBadge(count: Int) {
    Card(
        modifier = Modifier.shadow(
            elevation = 4.dp,
            shape = DeckBadgeShape,
            ambientColor = Color.Black,
            spotColor = Color.Black
        ),
        shape = DeckBadgeShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color.White.copy(alpha = DeckBadgeBorderAlpha))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(DeckBadgeShape)
                .background(DeckBadgeBackground)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = DeckBadgePaddingH, vertical = DeckBadgePaddingV),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = DeckBadgeIconWidth, height = DeckBadgeIconHeight)
                        .background(DeckBadgeIconBackground, DeckBadgeIconShape)
                        .border(1.dp, Color.White.copy(alpha = DeckBadgeIconBorderAlpha), DeckBadgeIconShape)
                )
                Spacer(modifier = Modifier.width(DeckBadgeIconSpacing))
                Text(
                    text = count.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BlockTargetDialog(
    playerList: List<PlayerTurn>,
    selfPlayerId: String,
    onPlayerSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_player_title)) },
        text = {
            Column {
                playerList.forEach { player ->
                    val label = if (player.playerId == selfPlayerId)
                        stringResource(R.string.player_name_self, player.playerName)
                    else
                        player.playerName
                    Button(
                        onClick = { onPlayerSelected(player.playerId) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        ToolIcons(player.blockedTools)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}

@Composable
fun DoubleRepairToolDialog(
    tools: List<String>,
    onToolSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_tool_title)) },
        text = {
            Column {
                tools.forEach { tool ->
                    Button(
                        onClick = { onToolSelected(tool) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            when(tool) {
                                "LANTERN" -> stringResource(R.string.repair_lantern)
                                "PICKAXE" -> stringResource(R.string.repair_pickaxe)
                                "CART" -> stringResource(R.string.repair_cart)
                                else -> tool
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}

@Composable
private fun GameSyncOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}


@Composable
private fun localizeServerError(code: String): String = when (code) {
    "error.invalid_placement" -> stringResource(R.string.error_invalid_placement)
    else -> code
}
