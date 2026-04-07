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

    // ── Session lifecycle ────────────────────────────────────────────────────

    @Test
    fun `afterConnectionEstablished adds session`() {
        handler.afterConnectionEstablished(session)
        // Session must receive the broadcast after being registered
        handler.broadcast("TEST", "DATA")
        verify(session).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `afterConnectionClosed removes session`() {
        handler.afterConnectionEstablished(session)
        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        // Session was removed — broadcast must not reach it
        val state = GameState(players = emptyList(), currentPlayerId = "test")
        handler.broadcast("GAME_STATE_UPDATE", state)

        verify(session, never()).sendMessage(any(TextMessage::class.java))
    }

    // ── START_GAME handling ──────────────────────────────────────────────────

    @Test
    fun `handleTextMessage START_GAME broadcasts GAME_STATE_UPDATE`() {
        // Minimum 3 players required by CardDistributor
        val players = listOf(Player("1", "Alice"), Player("2", "Bob"), Player("3", "Charlie"))
        val request = CreateGameRequest(players = players)
        val message = TextMessage(objectMapper.writeValueAsString(mapOf(
            "type" to "START_GAME",
            "data" to request
        )))

        val newState = GameState(
            players = listOf(
                PlayerTurn("1", "Alice", 1),
                PlayerTurn("2", "Bob", 2),
                PlayerTurn("3", "Charlie", 3)
            ),
            currentPlayerId = "1"
        )
        `when`(gameService.assignRandomTurnOrder(anyList())).thenReturn(newState)

        handler.afterConnectionEstablished(session)
        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        // Expect 2 broadcasts: GAME_STATE_UPDATE + CARDS_DEALT
        verify(session, times(2)).sendMessage(captor.capture())

        // First broadcast must be the game state
        val gameStatePayload = captor.allValues[0].payload
        assertTrue(gameStatePayload.contains("\"type\":\"GAME_STATE_UPDATE\""))
        assertTrue(gameStatePayload.contains("\"currentPlayerId\":\"1\""))
    }

    @Test
    fun `handleTextMessage START_GAME broadcasts CARDS_DEALT with hands for all players`() {
        // Minimum 3 players required by CardDistributor
        val players = listOf(Player("1", "Alice"), Player("2", "Bob"), Player("3", "Charlie"))
        val request = CreateGameRequest(players = players)
        val message = TextMessage(objectMapper.writeValueAsString(mapOf(
            "type" to "START_GAME",
            "data" to request
        )))

        val newState = GameState(
            players = listOf(
                PlayerTurn("1", "Alice", 1),
                PlayerTurn("2", "Bob", 2),
                PlayerTurn("3", "Charlie", 3)
            ),
            currentPlayerId = "1"
        )
        `when`(gameService.assignRandomTurnOrder(anyList())).thenReturn(newState)

        handler.afterConnectionEstablished(session)
        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        // Expect 2 broadcasts: GAME_STATE_UPDATE + CARDS_DEALT
        verify(session, times(2)).sendMessage(captor.capture())

        // Second broadcast must contain hands keyed by player ID
        val cardsDealtPayload = captor.allValues[1].payload
        assertTrue(cardsDealtPayload.contains("\"type\":\"CARDS_DEALT\""))
        assertTrue(cardsDealtPayload.contains("\"1\"")) // hand for Alice
        assertTrue(cardsDealtPayload.contains("\"2\"")) // hand for Bob
        assertTrue(cardsDealtPayload.contains("\"3\"")) // hand for Charlie
    }

    @Test
    fun `handleTextMessage START_GAME with null data does nothing`() {
        // Missing data field — handler must not call gameService
        val message = TextMessage("{\"type\":\"START_GAME\"}")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).assignRandomTurnOrder(anyList())
    }

    @Test
    fun `handleTextMessage with missing type does nothing`() {
        // No type field — handler must not call gameService
        val message = TextMessage("{\"data\":{}}")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).assignRandomTurnOrder(anyList())
    }

    @Test
    fun `handleTextMessage with unknown type does nothing`() {
        // Unrecognised message type — handler must ignore silently
        val message = TextMessage("{\"type\":\"UNKNOWN\",\"data\":{}}")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).assignRandomTurnOrder(anyList())
    }

    // ── Error handling ───────────────────────────────────────────────────────

    @Test
    fun `handleTextMessage handles exception with message`() {
        // Malformed JSON triggers catch block — session must receive ERROR
        val message = TextMessage("invalid json")
        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("\"type\":\"ERROR\""))
        assertTrue(captor.value.payload.contains("Unrecognized token"))
    }

    @Test
    fun `handleTextMessage handles exception without message`() {
        // Exception with null message falls back to "Unknown error"
        val mockMapper = mock(ObjectMapper::class.java)
        val handlerWithMock = WebSocketHandler(mockMapper, gameService)

        `when`(mockMapper.readTree(anyString())).thenThrow(RuntimeException())
        `when`(mockMapper.writeValueAsString(any())).thenReturn("{\"type\":\"ERROR\",\"data\":\"Unknown error\"}")

        handlerWithMock.handleTextMessage(session, TextMessage("{}"))

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("Unknown error"))
    }

    // ── Broadcast behaviour ──────────────────────────────────────────────────

    @Test
    fun `broadcast sends message to all open sessions`() {
        val session2 = mock(WebSocketSession::class.java)
        `when`(session2.isOpen).thenReturn(true)
        `when`(session2.id).thenReturn("session-2")

        handler.afterConnectionEstablished(session)
        handler.afterConnectionEstablished(session2)

        val state = GameState(players = emptyList(), currentPlayerId = "test")
        handler.broadcast("TEST_TYPE", state)

        // Both sessions must receive the broadcast
        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        verify(session2).sendMessage(any(TextMessage::class.java))
        assertTrue(captor.value.payload.contains("\"type\":\"TEST_TYPE\""))
    }

    @Test
    fun `broadcast skips closed sessions`() {
        val session2 = mock(WebSocketSession::class.java)
        `when`(session2.isOpen).thenReturn(false) // session2 is closed

        handler.afterConnectionEstablished(session)
        handler.afterConnectionEstablished(session2)

        handler.broadcast("TEST", "data")

        // Only open session must receive the message
        verify(session).sendMessage(any(TextMessage::class.java))
        verify(session2, never()).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `broadcast handles session sendMessage exception`() {
        `when`(session.isOpen).thenReturn(true)
        handler.afterConnectionEstablished(session)

        // IOException during send must not crash the handler
        doThrow(IOException("Socket closed")).`when`(session).sendMessage(any(TextMessage::class.java))
        handler.broadcast("TEST", "data")

        verify(session).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `broadcast handles session sendMessage exception without message`() {
        `when`(session.isOpen).thenReturn(true)
        handler.afterConnectionEstablished(session)

        // RuntimeException with null message must not crash the handler
        doThrow(RuntimeException()).`when`(session).sendMessage(any(TextMessage::class.java))
        handler.broadcast("TEST", "data")

        verify(session).sendMessage(any(TextMessage::class.java))
    }

    // ── Private sendMessage error paths ─────────────────────────────────────

    @Test
    fun `sendMessage handles exception`() {
        // sendMessage is private — triggered via handleTextMessage error path
        val message = TextMessage("invalid json")
        doThrow(IOException("Fail")).`when`(session).sendMessage(any(TextMessage::class.java))

        handler.handleTextMessage(session, message)

        verify(session).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `sendMessage handles exception without message`() {
        // RuntimeException with null message must not crash the error path
        val message = TextMessage("invalid json")
        doThrow(RuntimeException()).`when`(session).sendMessage(any(TextMessage::class.java))

        handler.handleTextMessage(session, message)

        verify(session).sendMessage(any(TextMessage::class.java))
    }
}