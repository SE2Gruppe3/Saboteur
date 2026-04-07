package com.aau.server

import com.aau.saboteur.model.CreateGameRequest
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.PlayerTurn
import com.aau.saboteur.model.Role
import com.aau.server.service.GameService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.WebSocketHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.*
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

class WebSocketHandlerTests {

    private lateinit var gameService: GameService
    private lateinit var messagingService: MessagingService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var handler: WebSocketHandler
    private lateinit var session: WebSocketSession

    @BeforeEach
    fun setup() {
        gameService = mock(GameService::class.java)
        messagingService = mock(MessagingService::class.java)
        objectMapper = jacksonObjectMapper()
        handler = WebSocketHandler(objectMapper, gameService, messagingService)
        session = mock(WebSocketSession::class.java)
        `when`(session.isOpen).thenReturn(true)
        `when`(session.id).thenReturn("test-session")
    }

    @Test
    fun `afterConnectionEstablished delegates to messagingService`() {
        handler.afterConnectionEstablished(session)
        verify(messagingService).addSession(session)
    }

    @Test
    fun `afterConnectionClosed delegates to messagingService`() {
        handler.afterConnectionClosed(session, CloseStatus.NORMAL)
        verify(messagingService).removeSession(session)
    }

    @Test
    fun `handleTextMessage START_GAME triggers game start and delegates to messagingService`() {
        val players = listOf(Player("1", "Alice"))
        val request = CreateGameRequest(players = players)
        val message = TextMessage(objectMapper.writeValueAsString(mapOf(
            "type" to "START_GAME",
            "data" to request
        )))
        
        val newState = GameState(
            players = listOf(PlayerTurn("1", "Alice", 1)),
            currentPlayerId = "1"
        )
        val playerWithRole = Player("1", "Alice", role = Role.GOLDDIGGER)
        val assignedPlayers = mapOf("1" to playerWithRole)
        
        `when`(gameService.assignRandomTurnOrder(anyList())).thenReturn(newState)
        `when`(gameService.assignRandomRoles(anyList())).thenReturn(assignedPlayers)

        handler.handleTextMessage(session, message)

        verify(gameService).assignRandomTurnOrder(anyList())
        verify(gameService).assignRandomRoles(anyList())

        verify(messagingService).broadcast("GAME_STATE_UPDATE", newState)
        verify(messagingService).sendToPlayer("1", "PLAYER_DATA", playerWithRole)
    }

    @Test
    fun `handleTextMessage START_GAME with null data does nothing`() {
        val message = TextMessage("{\"type\":\"START_GAME\"}")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).assignRandomTurnOrder(anyList())
    }

    @Test
    fun `handleTextMessage with missing type does nothing`() {
        val message = TextMessage("{\"data\":{}}")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).assignRandomTurnOrder(anyList())
    }
}
