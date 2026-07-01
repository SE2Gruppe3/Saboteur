package com.aau.server.websocket.command.handlers

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.LobbyCreateCommand
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.reflect.KClass

@Component
class LobbyCreateHandler(
    private val messagingService: MessagingService,
    private val lobbyService: LobbyService
) : CommandHandler<LobbyCreateCommand> {

    override val commandType: String = "LOBBY_CREATE"
    override val commandClass: KClass<LobbyCreateCommand> = LobbyCreateCommand::class

    override fun handle(session: WebSocketSession, command: LobbyCreateCommand) {
        val lobbyState = lobbyService.createLobby(command.playerName, visibility = command.visibility)
        // Bind session to player and lobby group
        messagingService.registerPlayer(session.id, lobbyState.players.first().id)
        messagingService.joinLobbyGroup(session.id, lobbyState.lobbyCode)
        
        // LobbyService.persist already handles the broadcasts
    }
}
