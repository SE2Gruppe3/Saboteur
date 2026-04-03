package com.aau.server

import com.aau.shared.game.CreateGameRequest
import com.aau.shared.game.GameState
import com.aau.shared.game.Player
import com.aau.shared.game.PlayerTurn
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException

class GameWebSocketHandlerTests {

    private lateinit var gameService: GameService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var handler: GameWebSocketHandler
    private lateinit var session: WebSocketSession

    @BeforeEach
    fun setup() {
        gameService = mock(GameService::class.java)
        objectMapper = jacksonObjectMapper()
        handler = GameWebSocketHandler(objectMapper, gameService)
        session = mock(WebSocketSession::class.java)
        `when`(session.isOpen).thenReturn(true)
    }

    @Test
    fun `afterConnectionEstablished sends current game state`() {
        val initialState = GameState(players = emptyList(), currentPlayerId = null)
        `when`(gameService.getGameState()).thenReturn(initialState)

        handler.afterConnectionEstablished(session)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        
        val payload = captor.value.payload
        assertTrue(payload.contains("\"players\":[]"))
    }

    @Test
    fun `afterConnectionClosed removes session`() {
        handler.afterConnectionEstablished(session)
        handler.afterConnectionClosed(session, CloseStatus.NORMAL)
        
        val state = GameState(players = emptyList(), currentPlayerId = "test")
        handler.broadcastGameState(state)

        verify(session, times(1)).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `handleTextMessage START_GAME triggers game start and broadcasts`() {
        val players = listOf(Player("1", "Alice"))
        val request = CreateGameRequest(players = players)
        val message = TextMessage(objectMapper.writeValueAsString(mapOf(
            "action" to "START_GAME",
            "data" to request
        )))
        
        val newState = GameState(
            players = listOf(PlayerTurn("1", "Alice", 1)),
            currentPlayerId = "1"
        )
        `when`(gameService.assignRandomTurnOrder(anyList())).thenReturn(newState)

        handler.afterConnectionEstablished(session)
        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session, times(2)).sendMessage(captor.capture())
        
        val lastPayload = captor.allValues.last().payload
        assertTrue(lastPayload.contains("\"currentPlayerId\":\"1\""))
    }

    @Test
    fun `handleTextMessage with unknown action does nothing`() {
        val message = TextMessage("{\"action\":\"UNKNOWN\"}")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).assignRandomTurnOrder(anyList())
    }

    @Test
    fun `handleTextMessage with invalid json handles exception`() {
        val message = TextMessage("invalid json")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).assignRandomTurnOrder(anyList())
    }

    @Test
    fun `broadcastGameState sends message to all open sessions`() {
        val session2 = mock(WebSocketSession::class.java)
        `when`(session2.isOpen).thenReturn(true)
        
        handler.afterConnectionEstablished(session)
        handler.afterConnectionEstablished(session2)
        
        val state = GameState(players = emptyList(), currentPlayerId = "test")
        handler.broadcastGameState(state)
        
        verify(session, atLeastOnce()).sendMessage(any(TextMessage::class.java))
        verify(session2, atLeastOnce()).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `broadcastGameState skips closed sessions`() {
        val session2 = mock(WebSocketSession::class.java)
        `when`(session2.isOpen).thenReturn(false)
        
        handler.afterConnectionEstablished(session2)
        
        val state = GameState(players = emptyList(), currentPlayerId = "test")
        handler.broadcastGameState(state)

        verify(session2, times(1)).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `broadcastGameState handles session sendMessage exception`() {
        `when`(session.isOpen).thenReturn(true)
        
        handler.afterConnectionEstablished(session) 

        doThrow(IOException("Socket closed")).`when`(session).sendMessage(any(TextMessage::class.java))
        
        val state = GameState(players = emptyList(), currentPlayerId = "test")
        handler.broadcastGameState(state) 

        verify(session, times(2)).sendMessage(any(TextMessage::class.java))
    }
}
