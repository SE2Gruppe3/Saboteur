package com.aau.server.websocket.command

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.TunnelCard

data class RegisterCommand(val playerId: String, val lobbyCode: String) : Command
data class StartGameCommand(val players: List<Player>) : Command
data class PlayCardCommand(
    val playerId: String,
    val cardId: String,
    val position: BoardPosition,
    val isRotated: Boolean
) : Command
data class DiscardCardCommand(val playerId: String, val cardId: String) : Command
data class LobbyLeaveCommand(val lobbyCode: String, val playerId: String) : Command
data class GetValidPositionsCommand(val cardId: String, val isRotated: Boolean) : Command
class LobbyListFetchCommand : Command
data class LobbyCreateCommand(val playerName: String) : Command
data class LobbyJoinCommand(val lobbyCode: String, val playerName: String) : Command
