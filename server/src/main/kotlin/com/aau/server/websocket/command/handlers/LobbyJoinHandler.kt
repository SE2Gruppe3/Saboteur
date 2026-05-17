package com.aau.server.websocket.command.handlers

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.LobbyJoinCommand
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.reflect.KClass

@Component
class LobbyJoinHandler(
    private val messagingService: MessagingService,
    private val lobbyService: LobbyService
) : CommandHandler<LobbyJoinCommand> {

    override val commandType: String = "LOBBY_JOIN"
    override val commandClass: KClass<LobbyJoinCommand> = LobbyJoinCommand::class

    override fun handle(session: WebSocketSession, command: LobbyJoinCommand) {
        val lobbyState = lobbyService.joinLobby(command.lobbyCode, command.playerName)
        val joinedPlayer = lobbyState.players.find { it.name == command.playerName }
            ?: lobbyState.players.last()
        
        // Bind session
        messagingService.registerPlayer(session.id, joinedPlayer.id)
        messagingService.joinLobbyGroup(session.id, lobbyState.lobbyCode)
    }
}
