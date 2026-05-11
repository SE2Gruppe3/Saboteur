package com.aau.server.websocket

import com.aau.saboteur.model.*
import com.aau.server.service.GameService
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
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
    private val lobbyService: LobbyService,
    private val turnManager: TurnManager
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

            when (type) {
                "START_GAME" -> {
                    if (data != null) {
                        val request = objectMapper.treeToValue<CreateGameRequest>(data)
                        val lobbyCode = messagingService.getLobbyCodeForSession(session.id)
                            ?: throw IllegalArgumentException("Session is not connected to a lobby")
                        val lobbyState = lobbyService.getLobby(lobbyCode)
                        val playerId = messagingService.getPlayerIdForSession(session.id)
                            ?: throw IllegalArgumentException("Session is not linked to a player")

                        require(lobbyState.hostId == playerId) {
                            "Only the host can start the game"
                        }

                        val result = gameService.startGame(request.players)
                        turnManager.initializeGame(result.cardDistribution, result.gameState)

                        val startedLobby = lobbyService.markGameStarted(lobbyCode)

                        messagingService.broadcastToLobby(lobbyCode, "LOBBY_STATE_UPDATE", startedLobby)
                        messagingService.broadcastToLobby(lobbyCode, "GAME_STATE_UPDATE", result.gameState)
                        result.playerRoles.forEach { (targetPlayerId, player) ->
                            messagingService.sendToPlayer(targetPlayerId, "PLAYER_DATA", player)
                        }
                        messagingService.broadcastToLobby(lobbyCode, "CARDS_DEALT", result.cardDistribution.hands)
                        messagingService.broadcast("LOBBY_LIST_UPDATE", lobbyService.getAllLobbies())
                    }
                }
                "PLAY_CARD" -> {
                    if (data != null) {
                        val request = objectMapper.treeToValue<PlayCardRequest>(data)
                        val lobbyCode = messagingService.getLobbyCodeForSession(session.id)
                            ?: throw IllegalArgumentException("Session is not connected to a lobby")
                        val sessionPlayerId = messagingService.getPlayerIdForSession(session.id)
                            ?: throw IllegalArgumentException("Session is not linked to a player")

                        // Guard against a client sending another player's ID in the payload.
                        require(sessionPlayerId == request.playerId) {
                            "Player ID mismatch: session belongs to $sessionPlayerId"
                        }

                        val result = turnManager.playCard(
                            playerId = request.playerId,
                            cardId = request.cardId,
                            position = request.position,
                            isRotated = request.isRotated
                        )

                        messagingService.broadcastToLobby(lobbyCode, "GAME_STATE_UPDATE", result.updatedGameState)
                        messagingService.broadcastToLobby(lobbyCode, "CARDS_DEALT", result.updatedHands)
                    }
                }
                "DISCARD_CARD" -> {
                    if (data != null) {
                        val request = objectMapper.treeToValue<DiscardCardRequest>(data)
                        val lobbyCode = messagingService.getLobbyCodeForSession(session.id)
                            ?: throw IllegalArgumentException("Session is not connected to a lobby")
                        val sessionPlayerId = messagingService.getPlayerIdForSession(session.id)
                            ?: throw IllegalArgumentException("Session is not linked to a player")

                        // Guard against a client sending another player's ID in the payload.
                        require(sessionPlayerId == request.playerId) {
                            "Player ID mismatch: session belongs to $sessionPlayerId"
                        }

                        val result = turnManager.discardCard(
                            playerId = request.playerId,
                            cardId = request.cardId
                        )

                        messagingService.broadcastToLobby(lobbyCode, "GAME_STATE_UPDATE", result.updatedGameState)
                        messagingService.broadcastToLobby(lobbyCode, "CARDS_DEALT", result.updatedHands)
                    }
                }
                "LOBBY_CREATE" -> {
                    if (data != null) {
                        val request = objectMapper.treeToValue<LobbyCreateRequest>(data)
                        val lobbyState = lobbyService.createLobby(request.playerName)
                        messagingService.joinLobbyGroup(session.id, lobbyState.lobbyCode)
                        messagingService.registerPlayer(session.id, lobbyState.players.first().id)
                        messagingService.broadcastToLobby(lobbyState.lobbyCode, "LOBBY_STATE_UPDATE", lobbyState)
                        messagingService.broadcast("LOBBY_LIST_UPDATE", lobbyService.getAllLobbies())
                    }
                }
                "LOBBY_JOIN" -> {
                    if (data != null) {
                        val request = objectMapper.treeToValue<LobbyJoinRequest>(data)
                        val lobbyState = lobbyService.joinLobby(request.lobbyCode, request.playerName)
                        val joinedPlayer = lobbyState.players.last()
                        messagingService.joinLobbyGroup(session.id, lobbyState.lobbyCode)
                        messagingService.registerPlayer(session.id, joinedPlayer.id)
                        messagingService.broadcastToLobby(lobbyState.lobbyCode, "LOBBY_STATE_UPDATE", lobbyState)
                        messagingService.broadcast("LOBBY_LIST_UPDATE", lobbyService.getAllLobbies())
                    }
                }
                "LOBBY_LEAVE" -> {
                    if (data != null) {
                        val request = objectMapper.treeToValue<LobbyLeaveRequest>(data)
                        val updatedLobby = lobbyService.leaveLobby(request.lobbyCode, request.playerId)

                        messagingService.leaveLobbyGroup(session.id, request.lobbyCode)
                        messagingService.sendToSession(session.id, "LOBBY_LEFT", "")

                        if (updatedLobby != null) {
                            messagingService.broadcastToLobby(request.lobbyCode, "LOBBY_STATE_UPDATE", updatedLobby)
                        }
                        messagingService.broadcast("LOBBY_LIST_UPDATE", lobbyService.getAllLobbies())
                    }
                }
                "LOBBY_LIST_FETCH" -> {
                    messagingService.sendToSession(session.id, "LOBBY_LIST_UPDATE", lobbyService.getAllLobbies())
                }
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