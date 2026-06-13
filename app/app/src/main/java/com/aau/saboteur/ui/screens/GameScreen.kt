package com.aau.saboteur.ui.screens

import android.content.Context
import android.hardware.camera2.CameraManager
import androidx.compose.foundation.BorderStroke
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
import com.aau.saboteur.cheat.LocalVolumeKeyCheatHandlerRegistrar
import com.aau.saboteur.cheat.VolumeKeyCheatDetector
import com.aau.saboteur.model.*
import com.aau.saboteur.network.game.MapResult
import com.aau.saboteur.ui.components.*
import com.aau.saboteur.viewModels.GameViewModel
import com.aau.saboteur.viewModels.LobbyViewModel

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

private data class TopGameControlsState(
    val players: List<PlayerTurn>,
    val currentPlayerId: String?,
    val localPlayerId: String?,
    val menuOpen: Boolean,
    val volume: Float
)

private data class TopGameControlsActions(
    val onMenuToggle: () -> Unit,
    val onMenuDismiss: () -> Unit,
    val onVolumeChange: (Float) -> Unit,
    val onLeaveGame: () -> Unit,
    val onShowSpielregeln: () -> Unit
)

private data class PlayerHandPanelState(
    val currentHand: List<TunnelCard>?,
    val role: Role?,
    val remainingDeckSize: Int,
    val isMyTurn: Boolean,
    val selectedCard: TunnelCard?
)

private data class PlayerHandPanelActions(
    val onDiscardSelected: () -> Unit,
    val onCardSelected: (TunnelCard) -> Unit,
    val onCardRotated: (TunnelCard, Boolean) -> Unit
)

private data class TargetSelectionState(
    val selected: TunnelCard?,
    val showBlockDialog: Boolean,
    val showToolDialog: Boolean,
    val pendingToolSelection: Pair<String, List<String>>?,
    val players: List<PlayerTurn>,
    val selfPlayerId: String
)

private data class TargetSelectionActions(
    val playBlockCard: (String) -> Unit,
    val playRepairCard: (String, String) -> Unit,
    val onBlockDialogChange: (Boolean) -> Unit,
    val onToolDialogChange: (Boolean) -> Unit,
    val onPendingToolSelectionChange: (Pair<String, List<String>>?) -> Unit
)

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

    val sortedPlayers = uiState.gameState.players
    val currentHand = uiState.localPlayerId?.let { uiState.hands?.get(it) }
    val isMyTurn = uiState.localPlayerId != null &&
            uiState.gameState.currentPlayerId == uiState.localPlayerId &&
            !uiState.isSyncing

    GameScreenEffects(
        viewModel = viewModel,
        localPlayerId = localPlayerId,
        lobbyCode = lobbyCode,
        isMyTurn = isMyTurn,
        onRoundResultRequested = { showRoundResult = true },
        onFinalResultRequested = { showFinalResult = true }
    )

    var menuOpen by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var showSpielregeln by remember { mutableStateOf(false) }

    var showBlockDialog by remember { mutableStateOf(false) }
    var showToolDialog by remember { mutableStateOf(false) }
    var pendingToolSelection by remember { mutableStateOf<Pair<String, List<String>>?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        MapResultDialog(
            result = uiState.lastMapResult,
            onDismiss = { viewModel.dismissMapResult() }
        )
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
        TopGameControls(
            state = TopGameControlsState(
                players = sortedPlayers,
                currentPlayerId = uiState.gameState.currentPlayerId,
                localPlayerId = uiState.localPlayerId,
                menuOpen = menuOpen,
                volume = volume
            ),
            actions = TopGameControlsActions(
                onMenuToggle = { menuOpen = !menuOpen },
                onMenuDismiss = { menuOpen = false },
                onVolumeChange = { volume = it },
                onLeaveGame = onBackToLobby,
                onShowSpielregeln = { showSpielregeln = true }
            )
        )
        ErrorMessageBanner(errorMessage = uiState.errorMessage)
        PlayerHandPanel(
            state = PlayerHandPanelState(
                currentHand = currentHand,
                role = uiState.player?.role,
                remainingDeckSize = uiState.remainingDeckSize,
                isMyTurn = isMyTurn,
                selectedCard = uiState.selectedCard
            ),
            actions = PlayerHandPanelActions(
                onDiscardSelected = { viewModel.discardSelectedCard() },
                onCardSelected = { card ->
                    viewModel.selectCard(card)
                    if (needsTargetDialog(card.type)) showBlockDialog = true
                },
                onCardRotated = { card, rotated -> viewModel.onCardRotated(card, rotated) }
            )
        )

        if (uiState.isSyncing) {
            GameSyncOverlay()
        }

        TargetSelectionDialogs(
            state = TargetSelectionState(
                selected = uiState.selectedCard,
                showBlockDialog = showBlockDialog,
                showToolDialog = showToolDialog,
                pendingToolSelection = pendingToolSelection,
                players = uiState.gameState.players,
                selfPlayerId = uiState.localPlayerId.orEmpty()
            ),
            actions = TargetSelectionActions(
                playBlockCard = { viewModel.playBlockCardOnPlayer(it) },
                playRepairCard = { targetId, tool -> viewModel.playRepairCardOnPlayer(targetId, tool) },
                onBlockDialogChange = { showBlockDialog = it },
                onToolDialogChange = { showToolDialog = it },
                onPendingToolSelectionChange = { pendingToolSelection = it }
            )
        )

        if (showSpielregeln) {
            SpielregelnDialog(onDismiss = { showSpielregeln = false })
        }

        ResultScreens(
            showRoundResult = showRoundResult,
            showFinalResult = showFinalResult,
            roundResult = uiState.gameState.lastRoundResult,
            players = uiState.gameState.players,
            onNextRound = { showRoundResult = false },
            onBackToLobby = {
                showFinalResult = false
                onBackToActiveLobby()
            }
        )
    }
}

@Composable
private fun GameScreenEffects(
    viewModel: GameViewModel,
    localPlayerId: String?,
    lobbyCode: String?,
    isMyTurn: Boolean,
    onRoundResultRequested: () -> Unit,
    onFinalResultRequested: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.roundResultScreenRequested.collect {
            onRoundResultRequested()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.finalResultScreenRequested.collect {
            onFinalResultRequested()
        }
    }

    LaunchedEffect(localPlayerId, lobbyCode) {
        val playerId = localPlayerId
        val code = lobbyCode
        if (playerId != null && code != null) {
            viewModel.initGameSession(code, playerId)
        }
    }

    val context = LocalContext.current
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val currentIsMyTurn by rememberUpdatedState(isMyTurn)
    val currentViewModel by rememberUpdatedState(viewModel)
    DisposableEffect(cameraManager) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (enabled && currentIsMyTurn) {
                    currentViewModel.triggerCheat(CheatType.LANTERN_FLASHLIGHT)
                }
            }
        }
        cameraManager.registerTorchCallback(callback, null)
        onDispose {
            cameraManager.unregisterTorchCallback(callback)
        }
    }

    val registerVolumeKeyCheatHandler = LocalVolumeKeyCheatHandlerRegistrar.current
    val volumeKeyCheatDetector = remember { VolumeKeyCheatDetector() }
    DisposableEffect(registerVolumeKeyCheatHandler, volumeKeyCheatDetector) {
        registerVolumeKeyCheatHandler { direction ->
            if (volumeKeyCheatDetector.onKeyPressed(direction)) {
                currentViewModel.triggerCheat(CheatType.VOLUME_SEQUENCE_DISCARD)
            }
            true
        }
        onDispose {
            registerVolumeKeyCheatHandler(null)
        }
    }
}

@Composable
private fun MapResultDialog(result: MapResult?, onDismiss: () -> Unit) {
    if (result == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.secret_info_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.peek_goal_card_message))
                Spacer(modifier = Modifier.height(12.dp))
                val cardName = if (result.card.isGoal) {
                    stringResource(R.string.gold_found)
                } else {
                    stringResource(R.string.only_stone)
                }
                Text(cardName, style = MaterialTheme.typography.headlineMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok_button)) }
        }
    )
}

@Composable
private fun BoxScope.TopGameControls(
    state: TopGameControlsState,
    actions: TopGameControlsActions
) {
    if (state.players.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                    players = state.players,
                    currentPlayerId = state.currentPlayerId,
                    localPlayerId = state.localPlayerId
                )
            }
            Box {
                MenuButton(isOpen = state.menuOpen, onToggle = actions.onMenuToggle)
                LobbyMenu(
                    expanded = state.menuOpen,
                    onDismiss = actions.onMenuDismiss,
                    volume = state.volume,
                    onVolumeChange = actions.onVolumeChange,
                    onLeaveGame = actions.onLeaveGame,
                    onShowSpielregeln = actions.onShowSpielregeln
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ErrorMessageBanner(errorMessage: String?) {
    if (errorMessage == null) return

    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 4.dp
        ) {
            Text(
                text = localizeServerError(errorMessage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun BoxScope.PlayerHandPanel(
    state: PlayerHandPanelState,
    actions: PlayerHandPanelActions
) {
    if (state.currentHand == null) return

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
            state.role?.let {
                RoleCardView(role = it, compact = true)
            }
            Spacer(modifier = Modifier.width(8.dp))
            DeckBadge(count = state.remainingDeckSize)
        }
        if (state.isMyTurn && state.selectedCard != null) {
            Button(
                onClick = actions.onDiscardSelected,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(stringResource(R.string.discard_card_button), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        PlayerHandRow(
            modifier = Modifier.fillMaxWidth(),
            hand = state.currentHand,
            selectedCardId = state.selectedCard?.id,
            onCardSelected = { card ->
                if (state.isMyTurn) {
                    actions.onCardSelected(card)
                }
            },
            onCardRotated = actions.onCardRotated
        )
    }
}

@Composable
private fun TargetSelectionDialogs(
    state: TargetSelectionState,
    actions: TargetSelectionActions
) {
    val selected = state.selected
    if (selected != null && needsTargetDialog(selected.type) && state.showBlockDialog) {
        BlockTargetDialog(
            playerList = state.players,
            selfPlayerId = state.selfPlayerId,
            onPlayerSelected = { targetId ->
                handleTargetPlayerSelected(
                    selected = selected,
                    targetId = targetId,
                    players = state.players,
                    actions = actions
                )
            },
            onDismiss = { actions.onBlockDialogChange(false) }
        )
    }

    if (state.showToolDialog && state.pendingToolSelection != null) {
        val (targetId, tools) = state.pendingToolSelection
        DoubleRepairToolDialog(
            tools = tools,
            onToolSelected = { tool ->
                actions.playRepairCard(targetId, tool)
                actions.onToolDialogChange(false)
                actions.onPendingToolSelectionChange(null)
            },
            onDismiss = {
                actions.onToolDialogChange(false)
                actions.onPendingToolSelectionChange(null)
            }
        )
    }
}

private fun handleTargetPlayerSelected(
    selected: TunnelCard,
    targetId: String,
    players: List<PlayerTurn>,
    actions: TargetSelectionActions
) {
    if (isBlockCard(selected.type)) {
        actions.playBlockCard(targetId)
        actions.onBlockDialogChange(false)
        return
    }

    if (!isRepairCard(selected.type)) return

    val repairTools = getRepairToolsFromCard(selected.type)
    val player = players.find { it.playerId == targetId }
    if (player == null) {
        actions.onBlockDialogChange(false)
        return
    }

    val blocked = repairTools.filter { tool -> isToolBlocked(player.blockedTools, tool) }
    when (blocked.size) {
        0 -> actions.playRepairCard(targetId, repairTools.first())
        1 -> actions.playRepairCard(targetId, blocked.first())
        else -> {
            actions.onPendingToolSelectionChange(Pair(targetId, blocked))
            actions.onToolDialogChange(true)
        }
    }
    actions.onBlockDialogChange(false)
}

@Composable
private fun ResultScreens(
    showRoundResult: Boolean,
    showFinalResult: Boolean,
    roundResult: RoundResult?,
    players: List<PlayerTurn>,
    onNextRound: () -> Unit,
    onBackToLobby: () -> Unit
) {
    if (roundResult == null) return

    if (showRoundResult) {
        RoundResultScreen(
            roundResult = roundResult,
            players = players,
            onNextRound = onNextRound
        )
    }

    if (showFinalResult) {
        FinalResultScreen(
            roundResult = roundResult,
            players = players,
            onBackToLobby = onBackToLobby
        )
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
