package com.aau.server

import com.aau.saboteur.model.CreateGameRequest
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.PlayerTurn
import com.aau.saboteur.model.WsMessage
import com.aau.server.service.GameService
import com.aau.server.websocket.WebSocketHandler
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException

class WebSocketHandlerTests {

    private lateinit var gameService: GameService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var handler: WebSocketHandler
    private lateinit var session: WebSocketSession

    @BeforeEach
    fun setup() {
        gameService = mock(GameService::class.java)
        objectMapper = jacksonObjectMapper()
        handler = WebSocketHandler(objectMapper, gameService)
        session = mock(WebSocketSession::class.java)
        `when`(session.isOpen).thenReturn(true)
        `when`(session.id).thenReturn("test-session")
    }

    @Test
    fun `afterConnectionEstablished adds session`() {
        handler.afterConnectionEstablished(session)
        // Verify broadcast still works with this session
        handler.broadcast("TEST", "DATA")
        verify(session).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `afterConnectionClosed removes session`() {
        handler.afterConnectionEstablished(session)
        handler.afterConnectionClosed(session, CloseStatus.NORMAL)
        
        val state = GameState(players = emptyList(), currentPlayerId = "test")
        handler.broadcast("GAME_STATE_UPDATE", state)

        verify(session, never()).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `handleTextMessage START_GAME triggers game start and broadcasts`() {
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
        `when`(gameService.assignRandomTurnOrder(anyList())).thenReturn(newState)

        handler.afterConnectionEstablished(session)
        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session, times(1)).sendMessage(captor.capture())
        
        val lastPayload = captor.value.payload
        assertTrue(lastPayload.contains("\"type\":\"GAME_STATE_UPDATE\""))
        assertTrue(lastPayload.contains("\"currentPlayerId\":\"1\""))
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

    @Test
    fun `handleTextMessage with unknown type does nothing`() {
        val message = TextMessage("{\"type\":\"UNKNOWN\",\"data\":{}}")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).assignRandomTurnOrder(anyList())
    }

    @Test
    fun `handleTextMessage handles exception with message`() {
        val message = TextMessage("invalid json")
        handler.handleTextMessage(session, message)
        
        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("\"type\":\"ERROR\""))
        assertTrue(captor.value.payload.contains("Unrecognized token"))
    }

    @Test
    fun `handleTextMessage handles exception without message`() {
        val mockMapper = mock(ObjectMapper::class.java)
        val handlerWithMock = WebSocketHandler(mockMapper, gameService)
        
        `when`(mockMapper.readTree(anyString())).thenThrow(RuntimeException())
        `when`(mockMapper.writeValueAsString(any())).thenReturn("{\"type\":\"ERROR\",\"data\":\"Unknown error\"}")
        
        handlerWithMock.handleTextMessage(session, TextMessage("{}"))
        
        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("Unknown error"))
    }

    @Test
    fun `broadcast sends message to all open sessions`() {
        val session2 = mock(WebSocketSession::class.java)
        `when`(session2.isOpen).thenReturn(true)
        `when`(session2.id).thenReturn("session-2")
        
        handler.afterConnectionEstablished(session)
        handler.afterConnectionEstablished(session2)
        
        val state = GameState(players = emptyList(), currentPlayerId = "test")
        handler.broadcast("TEST_TYPE", state)
        
        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        verify(session2).sendMessage(any(TextMessage::class.java))
        
        assertTrue(captor.value.payload.contains("\"type\":\"TEST_TYPE\""))
    }

    @Test
    fun `broadcast skips closed sessions`() {
        val session2 = mock(WebSocketSession::class.java)
        `when`(session2.isOpen).thenReturn(false)
        
        handler.afterConnectionEstablished(session)
        handler.afterConnectionEstablished(session2)
        
        handler.broadcast("TEST", "data")
        
        verify(session).sendMessage(any(TextMessage::class.java))
        verify(session2, never()).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `broadcast handles session sendMessage exception`() {
        `when`(session.isOpen).thenReturn(true)
        handler.afterConnectionEstablished(session) 
        
        doThrow(IOException("Socket closed")).`when`(session).sendMessage(any(TextMessage::class.java))

        handler.broadcast("TEST", "data") 
        
        verify(session).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `broadcast handles session sendMessage exception without message`() {
        `when`(session.isOpen).thenReturn(true)
        handler.afterConnectionEstablished(session) 
        
        doThrow(RuntimeException()).`when`(session).sendMessage(any(TextMessage::class.java))
        
        handler.broadcast("TEST", "data") 
        
        verify(session).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `sendMessage handles exception`() {
        // sendMessage is private, so we trigger it via handleTextMessage error path
        val message = TextMessage("invalid json")
        doThrow(IOException("Fail")).`when`(session).sendMessage(any(TextMessage::class.java))
        
        handler.handleTextMessage(session, message)
        
        verify(session).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `sendMessage handles exception without message`() {
        val message = TextMessage("invalid json")
        doThrow(RuntimeException()).`when`(session).sendMessage(any(TextMessage::class.java))
        
        handler.handleTextMessage(session, message)
        
        verify(session).sendMessage(any(TextMessage::class.java))
    }
}
