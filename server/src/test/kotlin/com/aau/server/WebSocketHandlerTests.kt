package com.aau.server

import com.aau.saboteur.model.*
import com.aau.server.model.CardDistributionResult
import com.aau.server.model.GameStartResult
import com.aau.server.service.GameService
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.WebSocketHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException

class WebSocketHandlerTests {

    private lateinit var gameService: GameService
    private lateinit var lobbyService: LobbyService
    private lateinit var messagingService: MessagingService
    private lateinit var turnManager: TurnManager
    private lateinit var objectMapper: ObjectMapper
    private lateinit var handler: WebSocketHandler
    private lateinit var session: WebSocketSession

    @BeforeEach
    fun setup() {
        gameService = mock(GameService::class.java)
        lobbyService = mock(LobbyService::class.java)
        messagingService = mock(MessagingService::class.java)
        turnManager = mock(TurnManager::class.java)
        objectMapper = jacksonObjectMapper()

        handler = WebSocketHandler(objectMapper, gameService, messagingService, lobbyService, turnManager)

        session = mock(WebSocketSession::class.java)
        `when`(session.isOpen).thenReturn(true)
        `when`(session.id).thenReturn("test-session")
    }

    private fun createDummyCard() = TunnelCard(
        id = "dummy",
        type = CardType.START,
        connections = emptySet()
    )

    // Helpers for Mockito with Kotlin non-nullable types
    private fun <T> anyK(): T = any() ?: null as T
    private fun <T> eqK(value: T): T = eq(value) ?: value

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

    // ── LOBBY handling ───────────────────────────────────────────────────────

    @Test
    fun `handleTextMessage LOBBY_CREATE broadcasts LOBBY_STATE_UPDATE`() {
        val request = LobbyCreateRequest(playerName = "Host")
        val message = TextMessage(
            objectMapper.writeValueAsString(
                mapOf(
                    "type" to "LOBBY_CREATE",
                    "data" to request
                )
            )
        )

        val lobbyState = LobbyState(
            lobbyCode = "1234",
            hostId = "1",
            players = listOf(
                Player(id = "1", name = "Host")
            ),
            gameStarted = false
        )

        `when`(lobbyService.createLobby(anyString())).thenReturn(lobbyState)

        handler.handleTextMessage(session, message)

        verify(lobbyService).createLobby("Host")
        verify(messagingService).broadcastToLobby(eqK("1234"), eqK("LOBBY_STATE_UPDATE"), eqK(lobbyState))
        verify(messagingService).broadcast(eqK("LOBBY_LIST_UPDATE"), anyK())
    }

    @Test
    fun `handleTextMessage LOBBY_JOIN broadcasts LOBBY_STATE_UPDATE`() {
        val request = LobbyJoinRequest(lobbyCode = "1234", playerName = "Max")
        val message = TextMessage(
            objectMapper.writeValueAsString(
                mapOf(
                    "type" to "LOBBY_JOIN",
                    "data" to request
                )
            )
        )

        val lobbyState = LobbyState(
            lobbyCode = "1234",
            hostId = "1",
            players = listOf(
                Player(id = "1", name = "Host"),
                Player(id = "2", name = "Max")
            ),
            gameStarted = false
        )

        `when`(lobbyService.joinLobby(anyString(), anyString())).thenReturn(lobbyState)

        handler.handleTextMessage(session, message)

        verify(lobbyService).joinLobby("1234", "Max")
        verify(messagingService).broadcastToLobby(eqK("1234"), eqK("LOBBY_STATE_UPDATE"), eqK(lobbyState))
    }

    @Test
    fun `handleTextMessage LOBBY_LEAVE broadcasts update`() {
        val request = LobbyLeaveRequest(lobbyCode = "1234", playerId = "2")
        val message = TextMessage(
            objectMapper.writeValueAsString(
                mapOf(
                    "type" to "LOBBY_LEAVE",
                    "data" to request
                )
            )
        )

        val lobbyState = LobbyState(
            lobbyCode = "1234",
            hostId = "1",
            players = listOf(Player(id = "1", name = "Host")),
            gameStarted = false
        )

        `when`(lobbyService.leaveLobby(anyString(), anyString())).thenReturn(lobbyState)

        handler.handleTextMessage(session, message)

        verify(messagingService).leaveLobbyGroup("test-session", "1234")
        verify(messagingService).sendToSession(eqK("test-session"), eqK("LOBBY_LEFT"), anyK())
        verify(messagingService).broadcastToLobby(eqK("1234"), eqK("LOBBY_STATE_UPDATE"), eqK(lobbyState))
    }

    @Test
    fun `handleTextMessage LOBBY_LIST_FETCH sends current lobbies`() {
        val message = TextMessage("{\"type\":\"LOBBY_LIST_FETCH\"}")
        val lobbies = listOf(LobbyState("1234", "1", emptyList()))
        `when`(lobbyService.getAllLobbies()).thenReturn(lobbies)

        handler.handleTextMessage(session, message)

        verify(messagingService).sendToSession(eqK("test-session"), eqK("LOBBY_LIST_UPDATE"), eqK(lobbies))
    }

    // ── START_GAME handling ──────────────────────────────────────────────────

    @Test
    fun `handleTextMessage START_GAME broadcasts GAME_STATE_UPDATE`() {
        val players = listOf(Player("1", "Alice"), Player("2", "Bob"), Player("3", "Charlie"))
        val request = CreateGameRequest(players = players)
        val message = TextMessage(
            objectMapper.writeValueAsString(
                mapOf(
                    "type" to "START_GAME",
                    "data" to request
                )
            )
        )

        val lobbyCode = "1234"
        val hostId = "1"
        val lobbyState = LobbyState(lobbyCode, hostId, players)
        
        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn(lobbyCode)
        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn(hostId)
        `when`(lobbyService.getLobby(anyString())).thenReturn(lobbyState)

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

        `when`(gameService.startGame(anyList())).thenReturn(startResult)
        `when`(lobbyService.markGameStarted(anyString())).thenReturn(lobbyState.copy(gameStarted = true))

        handler.handleTextMessage(session, message)

        verify(messagingService).broadcastToLobby(eqK(lobbyCode), eqK("GAME_STATE_UPDATE"), eqK(newState))
        verify(messagingService).broadcastToLobby(eqK(lobbyCode), eqK("CARDS_DEALT"), anyK())
    }

    @Test
    fun `handleTextMessage START_GAME throws when not host`() {
        val players = listOf(Player("1", "Alice"), Player("2", "Bob"))
        val request = CreateGameRequest(players = players)
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to "START_GAME", "data" to request)))

        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn("1234")
        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn("2") // Not the host
        `when`(lobbyService.getLobby(anyString())).thenReturn(LobbyState("1234", "1", players))

        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("Only the host can start the game"))
    }

    @Test
    fun `handleTextMessage START_GAME throws when session not in lobby`() {
        val message = TextMessage("{\"type\":\"START_GAME\", \"data\": {\"players\": []}}")
        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn(null)

        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("Session is not connected to a lobby"))
    }

    @Test
    fun `handleTextMessage START_GAME throws when player not registered`() {
        val message = TextMessage("{\"type\":\"START_GAME\", \"data\": {\"players\": []}}")
        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn("1234")
        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn(null)

        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("Session is not linked to a player"))
    }

    // ── Generic handling ─────────────────────────────────────────────────────

    @Test
    fun `handleTextMessage with null data does nothing`() {
        val message = TextMessage("{\"type\":\"START_GAME\"}")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).startGame(anyList())
    }

    @Test
    fun `handleTextMessage with missing type does nothing`() {
        val message = TextMessage("{\"data\":{}}")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).startGame(anyList())
    }

    @Test
    fun `handleTextMessage with unknown type does nothing`() {
        val message = TextMessage("{\"type\":\"UNKNOWN\",\"data\":{}}")
        handler.handleTextMessage(session, message)
        verify(gameService, never()).startGame(anyList())
    }

    // ── Error handling ───────────────────────────────────────────────────────

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
        val handlerWithMock = WebSocketHandler(mockMapper, gameService, messagingService, lobbyService, turnManager)

        `when`(mockMapper.readTree(anyString())).thenThrow(RuntimeException())
        `when`(mockMapper.writeValueAsString(any())).thenReturn("{\"type\":\"ERROR\",\"data\":\"Unknown error\"}")

        handlerWithMock.handleTextMessage(session, TextMessage("{}"))

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session, atLeastOnce()).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("Unknown error"))
    }

    // ── Private sendMessage error paths ─────────────────────────────────────

    @Test
    fun `sendMessage handles exception`() {
        val message = TextMessage("invalid json")
        doThrow(IOException("Fail")).`when`(session).sendMessage(anyK())

        handler.handleTextMessage(session, message)

        verify(session, atLeastOnce()).sendMessage(anyK())
    }

    @Test
    fun `sendMessage handles exception without message`() {
        val message = TextMessage("invalid json")
        doThrow(RuntimeException()).`when`(session).sendMessage(anyK())

        handler.handleTextMessage(session, message)

        verify(session, atLeastOnce()).sendMessage(anyK())
    }
}
