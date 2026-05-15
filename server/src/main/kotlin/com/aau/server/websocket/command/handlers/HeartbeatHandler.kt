package com.aau.server.websocket.command.handlers

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.HeartbeatCommand
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.reflect.KClass

@Component
class HeartbeatHandler(
    private val messagingService: MessagingService,
    private val lobbyService: LobbyService
) : CommandHandler<HeartbeatCommand> {

    override val commandType: String = "HEARTBEAT"
    override val commandClass: KClass<HeartbeatCommand> = HeartbeatCommand::class

    override fun handle(session: WebSocketSession, command: HeartbeatCommand) {
        // Update activity tracking in both messaging and lobby services
        messagingService.updatePlayerActivity(command.playerId)
        lobbyService.updateActivity(command.lobbyCode)
        
        // No broadcast here - purely internal state update
        logger.trace("HEARTBEAT: Player {} in lobby {}", command.playerId, command.lobbyCode)
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(HeartbeatHandler::class.java)
    }
}
