package com.aau.server.websocket

import com.aau.saboteur.model.CreateGameRequest
import com.aau.saboteur.model.WsMessage
import com.aau.server.service.CardDistributor
import com.aau.server.service.GameService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.CopyOnWriteArrayList

@Component
class WebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val gameService: GameService
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(WebSocketHandler::class.java)
    private val sessions = CopyOnWriteArrayList<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session)
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val payload = message.payload
        try {
            val jsonNode = objectMapper.readTree(payload)
            val type = jsonNode["type"]?.asText()
            val data = jsonNode["data"]

            if (type == "START_GAME" && data != null) {
                val request = objectMapper.readValue<CreateGameRequest>(data.toString())
                val newState = gameService.assignRandomTurnOrder(request.players)
                val distribution = CardDistributor.distribute(request.players.map { it.id })
                broadcast("GAME_STATE_UPDATE", newState)
                broadcast("CARDS_DEALT", distribution.hands)
            }
        } catch (e: Exception) {
            logger.error("Error handling text message: {}", e.message)
            sendMessage(session, "ERROR", e.message ?: "Unknown error")
        }
    }

    fun broadcast(type: String, data: Any) {
        val message = createTextMessage(type, data)

        sessions.forEach { session ->
            if (session.isOpen) {
                try {
                    session.sendMessage(message)
                } catch (e: Exception) {
                    logger.error("Error sending broadcast to session {}: {}", session.id, e.message)
                }
            }
        }
    }

    private fun sendMessage(session: WebSocketSession, type: String, data: Any) {
        try {
            session.sendMessage(createTextMessage(type, data))
        } catch (e: Exception) {
            logger.error("Error sending message to session {}: {}", session.id, e.message)
        }
    }

    private fun createTextMessage(type: String, data: Any): TextMessage {
        return TextMessage(objectMapper.writeValueAsString(WsMessage(type, data)))
    }
}