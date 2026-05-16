package com.aau.server.websocket.command.handlers

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.LobbyLeaveCommand
import com.aau.server.websocket.event.GameEvent
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

@Component
class LobbyLeaveHandler(
    private val messagingService: MessagingService,
    private val lobbyService: LobbyService
) : CommandHandler<LobbyLeaveCommand> {

    override val commandType: String = "LOBBY_LEAVE"
    override val commandClass: KClass<LobbyLeaveCommand> = LobbyLeaveCommand::class

    override fun handle(session: WebSocketSession, command: LobbyLeaveCommand) {
        // Sequential processing per lobby
        messagingService.getLobbyLock(command.lobbyCode).withLock {
            lobbyService.leaveLobby(command.lobbyCode, command.playerId)
            
            // Clean up session mappings
            messagingService.leaveLobbyGroup(session.id, command.lobbyCode)
            messagingService.sendEventToSession(session.id, GameEvent.LobbyLeft())
            
            // Note: LobbyService.deleteLobbyInternal or persist handles the 
            // state update broadcasts for other players.
        }
    }
}
