package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.CheatType
import com.aau.saboteur.model.GameState
import com.aau.server.model.TurnResult
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
    private val session: WebSocketSession = mock()
    private lateinit var handler: PlayerCheatHandler

    @BeforeEach
    fun setUp() {
        handler = PlayerCheatHandler(messagingService, turnManager)
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

        verify(turnManager).cheatPlayer(lobbyCode, playerId, CheatType.LANTERN_FLASHLIGHT)
        verify(messagingService).sendEventToLobby(eq(lobbyCode), any())
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
