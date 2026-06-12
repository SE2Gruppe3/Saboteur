package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.GameState
import com.aau.server.model.TurnResult
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.DiscardCardCommand
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.locks.ReentrantLock

class DiscardCardHandlerTest {

    private val messagingService: MessagingService = mock()
    private val turnManager: TurnManager = mock()
    private val lobbyService: LobbyService = mock()
    private val session: WebSocketSession = mock()
    private lateinit var handler: DiscardCardHandler

    @BeforeEach
    fun setUp() {
        handler = DiscardCardHandler(messagingService, turnManager, lobbyService)
        whenever(session.id).thenReturn("session-1")
    }

    @Test
    fun `handle discard card successfully`() {
        val lobbyCode = "1234"
        val playerId = "player-1"
        val command = DiscardCardCommand(playerId, "card-1")
        val turnResult = TurnResult(GameState(emptyList(), "next-player"), emptyMap(), null)

        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(lobbyCode)
        whenever(messagingService.getLobbyLock(lobbyCode)).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(playerId)
        whenever(turnManager.discardCard(lobbyCode, playerId, "card-1")).thenReturn(turnResult)

        handler.handle(session, command)

        verify(turnManager).discardCard(lobbyCode, playerId, "card-1")
        verify(messagingService, times(2)).sendEventToLobby(eq(lobbyCode), any())
    }

    @Test
    fun `handle discard card with winner cleans up lobby when game over`() {
        val lobbyCode = "1234"
        val playerId = "player-1"
        val command = DiscardCardCommand(playerId, "card-1")
        val turnResult = TurnResult(
            GameState(emptyList(), "next-player", isGameOver = true),
            emptyMap(),
            "SABOTEURS"
        )

        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(lobbyCode)
        whenever(messagingService.getLobbyLock(lobbyCode)).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(playerId)
        whenever(turnManager.discardCard(lobbyCode, playerId, "card-1")).thenReturn(turnResult)

        handler.handle(session, command)

        verify(messagingService, times(3)).sendEventToLobby(eq(lobbyCode), any())
        verify(lobbyService).resetAfterGame(lobbyCode)
    }

    @Test
    fun `handle discard card with winner in middle round does not clean up lobby`() {
        val lobbyCode = "1234"
        val playerId = "player-1"
        val command = DiscardCardCommand(playerId, "card-1")
        val turnResult = TurnResult(
            GameState(emptyList(), "next-player", isGameOver = false),
            emptyMap(),
            "SABOTEURS"
        )

        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(lobbyCode)
        whenever(messagingService.getLobbyLock(lobbyCode)).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(playerId)
        whenever(turnManager.discardCard(lobbyCode, playerId, "card-1")).thenReturn(turnResult)

        handler.handle(session, command)

        verify(messagingService, times(3)).sendEventToLobby(eq(lobbyCode), any())
        verify(lobbyService, never()).resetAfterGame(lobbyCode)
    }

    @Test
    fun `handle discard card throws exception if playerId mismatch`() {
        val lobbyCode = "1234"
        val playerId = "player-1"
        val command = DiscardCardCommand("other-player", "card-1")

        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(lobbyCode)
        whenever(messagingService.getLobbyLock(lobbyCode)).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(playerId)

        assertThrows<IllegalArgumentException> {
            handler.handle(session, command)
        }
    }
}
