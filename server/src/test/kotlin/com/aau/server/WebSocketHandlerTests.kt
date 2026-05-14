package com.aau.server

import com.aau.saboteur.model.*
import com.aau.server.model.CardDistributionResult
import com.aau.server.model.GameStartResult
import com.aau.server.model.TurnResult
import org.mockito.ArgumentMatchers.anyBoolean
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

    // ── PLAY_CARD handling ───────────────────────────────────────────────────

    @Test
    fun `handleTextMessage PLAY_CARD broadcasts GAME_STATE_UPDATE and CARDS_DEALT`() {
        val request = PlayCardRequest("1", "card1", BoardPosition(3, 2), false)
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to "PLAY_CARD", "data" to request)))

        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn("1234")
        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn("1")

        val newState = GameState(listOf(PlayerTurn("1", "Alice", 1), PlayerTurn("2", "Bob", 2)), "2")
        val newHands = mapOf<String, List<TunnelCard>>("1" to emptyList(), "2" to listOf(createDummyCard()))
        val turnResult = TurnResult(newState, newHands)

        `when`(turnManager.playCard(anyString(), anyString(), anyK(), anyBoolean())).thenReturn(turnResult)

        handler.handleTextMessage(session, message)

        verify(turnManager).playCard(eqK("1"), eqK("card1"), eqK(BoardPosition(3, 2)), eqK(false))
        verify(messagingService).broadcastToLobby(eqK("1234"), eqK("GAME_STATE_UPDATE"), eqK(newState))
        verify(messagingService).broadcastToLobby(eqK("1234"), eqK("CARDS_DEALT"), eqK(newHands))
    }

    @Test
    fun `handleTextMessage PLAY_CARD throws when session not in lobby`() {
        val request = PlayCardRequest("1", "card1", BoardPosition(3, 2), false)
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to "PLAY_CARD", "data" to request)))

        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn(null)

        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("Session is not connected to a lobby"))
    }

    @Test
    fun `handleTextMessage PLAY_CARD throws when player not registered`() {
        val request = PlayCardRequest("1", "card1", BoardPosition(3, 2), false)
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to "PLAY_CARD", "data" to request)))

        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn("1234")
        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn(null)

        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("Session is not linked to a player"))
    }

    @Test
    fun `handleTextMessage PLAY_CARD throws when player ID mismatch`() {
        val request = PlayCardRequest("1", "card1", BoardPosition(3, 2), false)
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to "PLAY_CARD", "data" to request)))

        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn("1234")
        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn("2") // differs from request.playerId

        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("Player ID mismatch"))
    }

    // ── DISCARD_CARD handling ────────────────────────────────────────────────

    @Test
    fun `handleTextMessage DISCARD_CARD broadcasts GAME_STATE_UPDATE and CARDS_DEALT`() {
        val request = DiscardCardRequest("1", "card1")
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to "DISCARD_CARD", "data" to request)))

        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn("1234")
        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn("1")

        val newState = GameState(listOf(PlayerTurn("1", "Alice", 1), PlayerTurn("2", "Bob", 2)), "2")
        val newHands = mapOf<String, List<TunnelCard>>("1" to emptyList(), "2" to listOf(createDummyCard()))
        val turnResult = TurnResult(newState, newHands)

        `when`(turnManager.discardCard(anyString(), anyString())).thenReturn(turnResult)

        handler.handleTextMessage(session, message)

        verify(turnManager).discardCard(eqK("1"), eqK("card1"))
        verify(messagingService).broadcastToLobby(eqK("1234"), eqK("GAME_STATE_UPDATE"), eqK(newState))
        verify(messagingService).broadcastToLobby(eqK("1234"), eqK("CARDS_DEALT"), eqK(newHands))
    }

    @Test
    fun `handleTextMessage DISCARD_CARD throws when session not in lobby`() {
        val request = DiscardCardRequest("1", "card1")
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to "DISCARD_CARD", "data" to request)))

        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn(null)

        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("Session is not connected to a lobby"))
    }

    @Test
    fun `handleTextMessage DISCARD_CARD throws when player not registered`() {
        val request = DiscardCardRequest("1", "card1")
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to "DISCARD_CARD", "data" to request)))

        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn("1234")
        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn(null)

        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("Session is not linked to a player"))
    }

    @Test
    fun `handleTextMessage DISCARD_CARD throws when player ID mismatch`() {
        val request = DiscardCardRequest("1", "card1")
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to "DISCARD_CARD", "data" to request)))

        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn("1234")
        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn("2")

        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("Player ID mismatch"))
    }

    // ── START_GAME: playerRoles forEach ─────────────────────────────────────

    @Test
    fun `handleTextMessage START_GAME sends PLAYER_DATA to each player`() {
        val players = listOf(Player("1", "Alice"), Player("2", "Bob"), Player("3", "Charlie"))
        val request = CreateGameRequest(players = players)
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to "START_GAME", "data" to request)))

        val lobbyCode = "1234"
        val hostId = "1"
        val lobbyState = LobbyState(lobbyCode, hostId, players)

        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn(lobbyCode)
        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn(hostId)
        `when`(lobbyService.getLobby(anyString())).thenReturn(lobbyState)

        val newState = GameState(
            players = listOf(PlayerTurn("1", "Alice", 1), PlayerTurn("2", "Bob", 2), PlayerTurn("3", "Charlie", 3)),
            currentPlayerId = "1"
        )
        val alice = Player("1", "Alice")
        val bob   = Player("2", "Bob")
        val charlie = Player("3", "Charlie")
        val startResult = GameStartResult(
            gameState = newState,
            playerRoles = mapOf("1" to alice, "2" to bob, "3" to charlie),
            cardDistribution = CardDistributionResult(emptyMap(), emptyList(), emptyList(), createDummyCard())
        )

        `when`(gameService.startGame(anyList())).thenReturn(startResult)
        `when`(lobbyService.markGameStarted(anyString())).thenReturn(lobbyState.copy(gameStarted = true))

        handler.handleTextMessage(session, message)

        verify(messagingService).sendToPlayer(eqK("1"), eqK("PLAYER_DATA"), eqK(alice))
        verify(messagingService).sendToPlayer(eqK("2"), eqK("PLAYER_DATA"), eqK(bob))
        verify(messagingService).sendToPlayer(eqK("3"), eqK("PLAYER_DATA"), eqK(charlie))
    }

    // ── GET_VALID_POSITIONS handling ─────────────────────────────────────────

    @Test
    fun `handleTextMessage GET_VALID_POSITIONS sends VALID_POSITIONS to session`() {
        val message = TextMessage(
            objectMapper.writeValueAsString(
                mapOf("type" to "GET_VALID_POSITIONS", "data" to mapOf("cardId" to "card1", "isRotated" to false))
            )
        )

        val card = TunnelCard("card1", CardType.PATH, emptySet())
        val validPositions = listOf(BoardPosition(3, 2), BoardPosition(5, 2))
        val gameState = GameState(emptyList(), null)

        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn("1")
        `when`(turnManager.getHands()).thenReturn(mapOf("1" to listOf(card)))
        `when`(turnManager.getGameState()).thenReturn(gameState)
        `when`(turnManager.getValidPositions(anyK(), anyBoolean(), anyList())).thenReturn(validPositions)

        handler.handleTextMessage(session, message)

        verify(messagingService).sendToSession(
            eqK("test-session"),
            eqK("VALID_POSITIONS"),
            eqK(mapOf("positions" to validPositions))
        )
    }

    @Test
    fun `handleTextMessage GET_VALID_POSITIONS card not found sends ERROR`() {
        val message = TextMessage(
            objectMapper.writeValueAsString(
                mapOf("type" to "GET_VALID_POSITIONS", "data" to mapOf("cardId" to "missing", "isRotated" to false))
            )
        )

        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn("1")
        `when`(turnManager.getHands()).thenReturn(mapOf("1" to emptyList()))

        handler.handleTextMessage(session, message)

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.value.payload.contains("\"type\":\"ERROR\""))
    }

    // ── PLAY_CARD / DISCARD_CARD with winner ─────────────────────────────────

    @Test
    fun `handleTextMessage PLAY_CARD with winner broadcasts GAME_OVER`() {
        val request = PlayCardRequest("1", "card1", BoardPosition(3, 2), false)
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to "PLAY_CARD", "data" to request)))

        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn("1234")
        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn("1")

        val newState = GameState(listOf(PlayerTurn("1", "Alice", 1), PlayerTurn("2", "Bob", 2)), "2")
        val newHands = mapOf<String, List<TunnelCard>>("1" to emptyList(), "2" to listOf(createDummyCard()))
        `when`(turnManager.playCard(anyString(), anyString(), anyK(), anyBoolean()))
            .thenReturn(TurnResult(newState, newHands, "DWARVES"))

        handler.handleTextMessage(session, message)

        verify(messagingService).broadcastToLobby(eqK("1234"), eqK("GAME_OVER"), eqK(mapOf("winner" to "DWARVES")))
    }

    @Test
    fun `handleTextMessage DISCARD_CARD with winner broadcasts GAME_OVER`() {
        val request = DiscardCardRequest("1", "card1")
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to "DISCARD_CARD", "data" to request)))

        `when`(messagingService.getLobbyCodeForSession(anyString())).thenReturn("1234")
        `when`(messagingService.getPlayerIdForSession(anyString())).thenReturn("1")

        val newState = GameState(listOf(PlayerTurn("1", "Alice", 1), PlayerTurn("2", "Bob", 2)), "2")
        val newHands = mapOf<String, List<TunnelCard>>("1" to emptyList(), "2" to listOf(createDummyCard()))
        `when`(turnManager.discardCard(anyString(), anyString()))
            .thenReturn(TurnResult(newState, newHands, "SABOTEURS"))

        handler.handleTextMessage(session, message)

        verify(messagingService).broadcastToLobby(eqK("1234"), eqK("GAME_OVER"), eqK(mapOf("winner" to "SABOTEURS")))
    }

    // ── LOBBY_LEAVE: last player (null response) ─────────────────────────────

    @Test
    fun `handleTextMessage LOBBY_LEAVE when last player leaves does not broadcast state update`() {
        val request = LobbyLeaveRequest(lobbyCode = "1234", playerId = "1")
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to "LOBBY_LEAVE", "data" to request)))

        `when`(lobbyService.leaveLobby(anyString(), anyString())).thenReturn(null)

        handler.handleTextMessage(session, message)

        verify(messagingService).leaveLobbyGroup("test-session", "1234")
        verify(messagingService).sendToSession(eqK("test-session"), eqK("LOBBY_LEFT"), anyK())
        verify(messagingService, never()).broadcastToLobby(anyString(), eqK("LOBBY_STATE_UPDATE"), anyK())
        verify(messagingService).broadcast(eqK("LOBBY_LIST_UPDATE"), anyK())
    }
}
