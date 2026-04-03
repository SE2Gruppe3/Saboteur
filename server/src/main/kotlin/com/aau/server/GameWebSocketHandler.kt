package com.aau.server

import com.aau.shared.game.CreateGameRequest
import com.aau.shared.game.GameState
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.CopyOnWriteArrayList

@Component
class GameWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val gameService: GameService
) : TextWebSocketHandler() {

    private val sessions = CopyOnWriteArrayList<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
        val currentState = gameService.getGameState()
        session.sendMessage(TextMessage(objectMapper.writeValueAsString(currentState)))
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session)
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val payload = message.payload
        try {
            val jsonNode = objectMapper.readTree(payload)
            val action = jsonNode.get("action")?.asText()

            when (action) {
                "START_GAME" -> {
                    val requestData = jsonNode.get("data")
                    val request = objectMapper.readValue<CreateGameRequest>(requestData.toString())
                    val newState = gameService.assignRandomTurnOrder(request.players)
                    broadcastGameState(newState)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun broadcastGameState(gameState: GameState) {
        val payload = objectMapper.writeValueAsString(gameState)
        val message = TextMessage(payload)
        
        sessions.forEach { session ->
            if (session.isOpen) {
                try {
                    session.sendMessage(message)
                } catch (e: Exception) {
                    // Handle stale session
                }
            }
        }
    }
}
