package com.aau.server.websocket

import com.aau.saboteur.model.WsMessage
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.CommandDispatcher
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class WebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val messagingService: MessagingService,
    private val commandDispatcher: CommandDispatcher
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(WebSocketHandler::class.java)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        messagingService.addSession(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        messagingService.removeSession(session)
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val payload = message.payload
        try {
            val jsonNode = objectMapper.readTree(payload)
            val type = jsonNode["type"]?.asText() ?: return
            val data = jsonNode["data"] ?: objectMapper.createObjectNode()

            commandDispatcher.dispatch(session, type, data)
            
        } catch (e: Exception) {
            logger.error("Error handling text message: {}", e.message)
            sendErrorMessage(session, e.message ?: "Unknown error")
        }
    }

    private fun sendErrorMessage(session: WebSocketSession, message: String) {
        try {
            if (session.isOpen) {
                val errorMsg = TextMessage(
                    objectMapper.writeValueAsString(
                        WsMessage("ERROR", message)
                    )
                )
                session.sendMessage(errorMsg)
            }
        } catch (ex: Exception) {
            logger.error("Error sending error message: {}", ex.message)
        }
    }
}
