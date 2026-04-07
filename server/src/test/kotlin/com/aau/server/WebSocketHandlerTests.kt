package com.aau.server

import com.aau.saboteur.model.*
import com.aau.server.model.CardDistributionResult
import com.aau.server.model.GameStartResult
import com.aau.server.service.GameService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.WebSocketHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException

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

    // Helpers for Kotlin non-nullable parameters
    private inline fun <reified T> anyK(default: T): T = any(T::class.java) ?: default
    private inline fun <reified T> eqK(value: T): T = eq(value) ?: value

    private fun createDummyCard() = TunnelCard(
        id = "dummy",
        type = CardType.START,
        connections = emptySet()
    )

    // ── Session lifecycle ────────────────────────────────────────────────────

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

    // ── START_GAME handling ──────────────────────────────────────────────────

    @Test
    fun `handleTextMessage START_GAME broadcasts GAME_STATE_UPDATE`() {
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
        val startResult = GameStartResult(
            gameState = newState,
            playerRoles = emptyMap(),
            cardDistribution = CardDistributionResult(emptyMap(), emptyList(), emptyList(), createDummyCard())
        )
        `when`(gameService.startGame(anyK(emptyList<Player>()))).thenReturn(startResult)

        handler.handleTextMessage(session, message)

        verify(messagingService).broadcast("GAME_STATE_UPDATE", newState)
        verify(messagingService).broadcast(eqK("CARDS_DEALT"), anyK(emptyMap<String, List<TunnelCard>>()))
    }

    @Test
    fun `handleTextMessage START_GAME triggers game start and delegates to messagingService`() {
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
        val playerWithRole = Player("1", "Alice", role = Role.GOLDDIGGER)
        val assignedPlayers = mapOf("1" to playerWithRole)
        val startResult = GameStartResult(
            gameState = newState,
            playerRoles = assignedPlayers,
            cardDistribution = CardDistributionResult(emptyMap(), emptyList(), emptyList(), createDummyCard())
        )
        
        `when`(gameService.startGame(anyK(emptyList<Player>()))).thenReturn(startResult)

        handler.handleTextMessage(session, message)

        verify(gameService).startGame(anyK(emptyList<Player>()))

        verify(messagingService).broadcast("GAME_STATE_UPDATE", newState)
        verify(messagingService).sendToPlayer("1", "PLAYER_DATA", playerWithRole)
        verify(messagingService).broadcast(eqK("CARDS_DEALT"), anyK(emptyMap<String, List<TunnelCard>>()))
    }

    @Test
    fun `handleTextMessage START_GAME with null data does nothing`() {
        // Missing data field — handler must not call gameService
        val message = TextMessage("{\"type\":\"START_GAME\"}")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).startGame(anyK(emptyList<Player>()))
    }

    @Test
    fun `handleTextMessage with missing type does nothing`() {
        // No type field — handler must not call gameService
        val message = TextMessage("{\"data\":{}}")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).startGame(anyK(emptyList<Player>()))
    }

    @Test
    fun `handleTextMessage with unknown type does nothing`() {
        // Unrecognised message type — handler must ignore silently
        val message = TextMessage("{\"type\":\"UNKNOWN\",\"data\":{}}")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).startGame(anyK(emptyList<Player>()))
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
        val handlerWithMock = WebSocketHandler(mockMapper, gameService, messagingService)

        `when`(mockMapper.readTree(anyString() ?: "")).thenThrow(RuntimeException())
        `when`(mockMapper.writeValueAsString(any())).thenReturn("{\"type\":\"ERROR\",\"data\":\"Unknown error\"}")

        handlerWithMock.handleTextMessage(session, TextMessage("{}"))

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session, atLeastOnce()).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("Unknown error"))
    }

    // ── Private sendMessage error paths ─────────────────────────────────────

    @Test
    fun `sendMessage handles exception`() {
        // sendMessage is private — triggered via handleTextMessage error path
        val message = TextMessage("invalid json")
        doThrow(IOException("Fail")).`when`(session).sendMessage(anyK(TextMessage("")))

        handler.handleTextMessage(session, message)

        verify(session, atLeastOnce()).sendMessage(anyK(TextMessage("")))
    }

    @Test
    fun `sendMessage handles exception without message`() {
        // RuntimeException with null message must not crash the error path
        val message = TextMessage("invalid json")
        doThrow(RuntimeException()).`when`(session).sendMessage(anyK(TextMessage("")))

        handler.handleTextMessage(session, message)

        verify(session, atLeastOnce()).sendMessage(anyK(TextMessage("")))
    }
}
