package com.aau.server.websocket.command.handlers

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.LobbyCreateCommand
import com.aau.server.websocket.event.GameEvent
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
        val lobbyState = lobbyService.createLobby(command.playerName)
        messagingService.joinLobbyGroup(session.id, lobbyState.lobbyCode)
        messagingService.registerPlayer(session.id, lobbyState.players.first().id)
        
        messagingService.sendEventToLobby(lobbyState.lobbyCode, GameEvent.LobbyStateUpdate(lobbyState))
        messagingService.broadcastEvent(GameEvent.LobbyListUpdate(lobbyService.getAllLobbies()))
    }
}
