package com.aau.server.websocket.command

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.ToolType
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.model.CheatType

data class RegisterCommand(
    val playerId: String, 
    val lobbyCode: String,
    val reconnect: Boolean = false
) : Command

data class StartGameCommand(val players: List<Player>) : Command

data class PlayCardCommand(
    val playerId: String,
    val cardId: String,
    val position: BoardPosition,
    val isRotated: Boolean
) : Command

data class DiscardCardCommand(val playerId: String, val cardId: String) : Command
data class LobbyLeaveCommand(val lobbyCode: String, val playerId: String) : Command
data class LobbyKickCommand(val lobbyCode: String, val hostId: String, val targetPlayerId: String) : Command
data class GetValidPositionsCommand(val cardId: String, val isRotated: Boolean) : Command
class LobbyListFetchCommand : Command
data class LobbyCreateCommand(val playerName: String) : Command
data class LobbyJoinCommand(val lobbyCode: String, val playerName: String) : Command
data class HeartbeatCommand(val playerId: String, val lobbyCode: String) : Command

data class PlayBlockCardCommand(
    val playerId: String,
    val cardId: String,
    val targetPlayerId: String
) : Command

data class PlayRepairCardCommand(
    val playerId: String,
    val cardId: String,
    val targetPlayerId: String,
    val tool: ToolType
) : Command

data class PlayMapCardCommand(
    val playerId: String,
    val cardId: String,
    val targetPosition: BoardPosition
) : Command

data class PlayRockfallCardCommand(
    val playerId: String,
    val cardId: String,
    val targetPosition: BoardPosition
) : Command

data class PlayerCheatCommand(
    val lobbyCode: String,
    val cheatType: CheatType
) : Command

data class AccuseCheatCommand(
    val lobbyCode: String,
    val accusedPlayerId: String
) : Command

/**
 * Sent by the client after receiving and processing a RECONNECT_SNAPSHOT.
 * This signals the server to release the event buffer and start sending live updates.
 */
class SyncAckCommand : Command
