package com.aau.server.websocket.command.handlers

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.LobbyLeaveCommand
import com.aau.server.websocket.event.GameEvent
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.reflect.KClass

@Component
class LobbyLeaveHandler(
    private val messagingService: MessagingService,
    private val lobbyService: LobbyService
) : CommandHandler<LobbyLeaveCommand> {

    override val commandType: String = "LOBBY_LEAVE"
    override val commandClass: KClass<LobbyLeaveCommand> = LobbyLeaveCommand::class

    override fun handle(session: WebSocketSession, command: LobbyLeaveCommand) {
        val updatedLobby = lobbyService.leaveLobby(command.lobbyCode, command.playerId)

        messagingService.leaveLobbyGroup(session.id, command.lobbyCode)
        messagingService.sendEventToSession(session.id, GameEvent.LobbyLeft())

        if (updatedLobby != null) {
            messagingService.sendEventToLobby(command.lobbyCode, GameEvent.LobbyStateUpdate(updatedLobby))
        }
        messagingService.broadcastEvent(GameEvent.LobbyListUpdate(lobbyService.getAllLobbies()))
    }
}
