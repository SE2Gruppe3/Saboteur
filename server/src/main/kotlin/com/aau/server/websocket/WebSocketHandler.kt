package com.aau.server.websocket

import com.aau.saboteur.model.CreateGameRequest
import com.aau.server.service.GameService
import com.aau.server.service.MessagingService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class WebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val gameService: GameService,
    private val messagingService: MessagingService
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
            val type = jsonNode["type"]?.asText()
            val data = jsonNode["data"]

            if (type == "START_GAME" && data != null) {
                val request = objectMapper.readValue<CreateGameRequest>(data.toString())
                val newState = gameService.assignRandomTurnOrder(request.players)

                val assignedPlayers = gameService.assignRandomRoles(request.players)

                messagingService.broadcast("GAME_STATE_UPDATE", newState)
                
                assignedPlayers.forEach { (playerId, player) ->
                    messagingService.sendToPlayer(playerId, "PLAYER_DATA", player)
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling text message: {}", e.message)
        }
    }
}
