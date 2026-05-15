package com.aau.server.websocket.command.handlers

import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.SyncAckCommand
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.reflect.KClass

@Component
class SyncAckHandler(
    private val messagingService: MessagingService
) : CommandHandler<SyncAckCommand> {

    override val commandType: String = "SYNC_ACK"
    override val commandClass: KClass<SyncAckCommand> = SyncAckCommand::class

    override fun handle(session: WebSocketSession, command: SyncAckCommand) {
        // Release the event buffer for this session
        messagingService.setSessionSynced(session.id)
        logger.debug("Received SYNC_ACK for session {}", session.id)
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(SyncAckHandler::class.java)
    }
}
