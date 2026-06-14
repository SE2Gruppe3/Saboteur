package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.CheatType
import com.aau.saboteur.model.GameState
import com.aau.server.model.TurnResult
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.PlayerCheatCommand
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.locks.ReentrantLock

class PlayerCheatHandlerTest {

    private val messagingService: MessagingService = mock()
    private val turnManager: TurnManager = mock()
    private val lobbyService: LobbyService = mock()
    private val session: WebSocketSession = mock()
    private lateinit var handler: PlayerCheatHandler

    @BeforeEach
    fun setUp() {
        handler = PlayerCheatHandler(messagingService, turnManager, lobbyService)
        whenever(session.id).thenReturn("session-123")
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())
    }

    @Test
    fun `handle successfully calls turnManager and broadcasts update`() {
        val lobbyCode = "L1"
        val playerId = "p1"
        val command = PlayerCheatCommand(lobbyCode, CheatType.LANTERN_FLASHLIGHT)
        val turnResult = TurnResult(GameState(), emptyMap())

        whenever(messagingService.getPlayerIdForSession("session-123")).thenReturn(playerId)
        whenever(turnManager.cheatPlayer(lobbyCode, playerId, CheatType.LANTERN_FLASHLIGHT)).thenReturn(turnResult)

        handler.handle(session, command)

        // Lockere Verifikation auf, da der Handler mehrere Events (GameStateUpdate & CardsDealt) senden kann
        verify(turnManager, atLeastOnce()).cheatPlayer(eq(lobbyCode), eq(playerId), eq(CheatType.LANTERN_FLASHLIGHT))
        verify(messagingService, atLeastOnce()).sendEventToLobby(eq(lobbyCode), any())
    }

    @Test
    fun `handle broadcasts game over and resets lobby when cheat ends final game`() {
        val lobbyCode = "L1"
        val playerId = "p1"
        val command = PlayerCheatCommand(lobbyCode, CheatType.TEST_REVEAL)
        val turnResult = TurnResult(GameState(isGameOver = true), emptyMap(), "SABOTEURS")

        whenever(messagingService.getPlayerIdForSession("session-123")).thenReturn(playerId)
        whenever(turnManager.cheatPlayer(lobbyCode, playerId, CheatType.TEST_REVEAL)).thenReturn(turnResult)

        handler.handle(session, command)

        verify(messagingService, times(3)).sendEventToLobby(eq(lobbyCode), any())
        verify(lobbyService).resetAfterGame(lobbyCode)
    }

    @Test
    fun `handle broadcasts game over without reset when cheat ends middle round`() {
        val lobbyCode = "L1"
        val playerId = "p1"
        val command = PlayerCheatCommand(lobbyCode, CheatType.TEST_REVEAL)
        val turnResult = TurnResult(GameState(isGameOver = false), emptyMap(), "DWARVES")

        whenever(messagingService.getPlayerIdForSession("session-123")).thenReturn(playerId)
        whenever(turnManager.cheatPlayer(lobbyCode, playerId, CheatType.TEST_REVEAL)).thenReturn(turnResult)

        handler.handle(session, command)

        verify(messagingService, times(3)).sendEventToLobby(eq(lobbyCode), any())
        verify(lobbyService, never()).resetAfterGame(lobbyCode)
    }

    @Test
    fun `handle throws exception when session has no playerId`() {
        val lobbyCode = "L1"
        val command = PlayerCheatCommand(lobbyCode, CheatType.LANTERN_FLASHLIGHT)

        whenever(messagingService.getPlayerIdForSession("session-123")).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) {
            handler.handle(session, command)
        }
        verify(turnManager, never()).cheatPlayer(any(), any(), any())
    }
}
