package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.GameState
import com.aau.server.service.GameService
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.RegisterCommand
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.locks.ReentrantLock

class RegisterHandlerTest {

    private val messagingService: MessagingService = mock()
    private val lobbyService: LobbyService = mock()
    private val gameService: GameService = mock()
    private val turnManager: TurnManager = mock()
    private val session: WebSocketSession = mock()
    private lateinit var handler: RegisterHandler

    @BeforeEach
    fun setUp() {
        handler = RegisterHandler(messagingService, lobbyService, gameService, turnManager)
        whenever(session.id).thenReturn("session-1")
    }

    @Test
    fun `handle register successfully for new game`() {
        val lobbyCode = "1234"
        val playerId = "p1"
        val command = RegisterCommand(playerId, lobbyCode, false)
        val player = Player(playerId, "Alice")
        val lobbyState = LobbyState(lobbyCode, playerId, listOf(player), false)

        whenever(messagingService.getLobbyLock(lobbyCode)).thenReturn(ReentrantLock())
        whenever(lobbyService.getLobby(lobbyCode)).thenReturn(lobbyState)

        handler.handle(session, command)

        verify(messagingService).registerPlayer("session-1", playerId)
        verify(messagingService).joinLobbyGroup("session-1", lobbyCode)
        verify(messagingService).sendEventToSession(eq("session-1"), any())
    }

    @Test
    fun `handle register successfully for ongoing game`() {
        val lobbyCode = "1234"
        val playerId = "p1"
        val command = RegisterCommand(playerId, lobbyCode, true)
        val player = Player(playerId, "Alice")
        val lobbyState = LobbyState(lobbyCode, playerId, listOf(player), true)
        val gameState = GameState(emptyList(), playerId)

        whenever(messagingService.getLobbyLock(lobbyCode)).thenReturn(ReentrantLock())
        whenever(lobbyService.getLobby(lobbyCode)).thenReturn(lobbyState)
        whenever(turnManager.getGameState(lobbyCode)).thenReturn(gameState)
        whenever(gameService.getPlayer(lobbyCode, playerId)).thenReturn(player)
        whenever(turnManager.getHands(lobbyCode)).thenReturn(mapOf(playerId to emptyList()))

        handler.handle(session, command)

        verify(messagingService).sendEventToSession(eq("session-1"), any())
    }

    @Test
    fun `handle register sends LobbyNotFound when lobby missing`() {
        val lobbyCode = "unknown"
        val playerId = "p1"
        val command = RegisterCommand(playerId, lobbyCode, false)

        whenever(messagingService.getLobbyLock(lobbyCode)).thenReturn(ReentrantLock())
        whenever(lobbyService.getLobby(lobbyCode)).thenThrow(IllegalArgumentException("Lobby not found"))

        handler.handle(session, command)

        verify(messagingService).sendEventToSession(eq("session-1"), any())
        verify(messagingService).setSessionSynced("session-1")
    }
}
