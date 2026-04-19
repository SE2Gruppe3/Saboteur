package com.aau.server.websocket

import com.aau.saboteur.model.CreateGameRequest
import com.aau.saboteur.model.LobbyCreateRequest
import com.aau.saboteur.model.LobbyJoinRequest
import com.aau.saboteur.model.WsMessage
import com.aau.server.service.GameService
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
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
    private val messagingService: MessagingService,
    private val lobbyService: LobbyService
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
                val request = objectMapper.treeToValue<CreateGameRequest>(data)

                val result = gameService.startGame(request.players)

                messagingService.broadcast("GAME_STATE_UPDATE", result.gameState)

                result.playerRoles.forEach { (playerId, player) ->
                    messagingService.sendToPlayer(playerId, "PLAYER_DATA", player)
                }

                messagingService.broadcast("CARDS_DEALT", result.cardDistribution.hands)
            } else if (type == "LOBBY_CREATE" && data != null) {
                val request = objectMapper.treeToValue<LobbyCreateRequest>(data)
                lobbyService.createLobby(session, request.playerName)
            } else if (type == "LOBBY_JOIN" && data != null) {
                val request = objectMapper.treeToValue<LobbyJoinRequest>(data)
                lobbyService.joinLobby(session, request.lobbyCode, request.playerName)
            }
        } catch (e: Exception) {
            logger.error("Error handling text message: {}", e.message)
            try {
                val errorMsg = TextMessage(
                    objectMapper.writeValueAsString(
                        WsMessage("ERROR", e.message ?: "Unknown error")
                    )
                )
                session.sendMessage(errorMsg)
            } catch (ex: Exception) {
                logger.error("Error sending error message: {}", ex.message)
            }
        }
    }
}