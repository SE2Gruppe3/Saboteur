package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.Player
import com.aau.server.service.GameLifecycleService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.StartGameCommand
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.locks.ReentrantLock

class StartGameHandlerTest {

    private val messagingService: MessagingService = mock()
    private val gameLifecycleService: GameLifecycleService = mock()
    private val session: WebSocketSession = mock()
    private lateinit var handler: StartGameHandler

    @BeforeEach
    fun setUp() {
        handler = StartGameHandler(messagingService, gameLifecycleService)
        whenever(session.id).thenReturn("session-1")
    }

    @Test
    fun `handle start game successfully`() {
        val lobbyCode = "1234"
        val playerId = "p1"
        val players = listOf(Player("p1", "Alice"), Player("p2", "Bob"), Player("p3", "Charlie"))
        val command = StartGameCommand(players)

        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(lobbyCode)
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(playerId)
        whenever(messagingService.getLobbyLock(lobbyCode)).thenReturn(ReentrantLock())

        handler.handle(session, command)

        verify(gameLifecycleService).startGame(eq(lobbyCode), eq(playerId), eq(players))
    }

    @Test
    fun `handle start game throws exception if not connected to lobby`() {
        val command = StartGameCommand(emptyList())
        whenever(messagingService.getLobbyCodeForSession(any())).thenReturn(null)

        assertThrows<IllegalArgumentException> {
            handler.handle(session, command)
        }
    }

    @Test
    fun `handle start game throws exception if player not linked`() {
        val lobbyCode = "1234"
        val command = StartGameCommand(emptyList())
        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(lobbyCode)
        whenever(messagingService.getPlayerIdForSession(any())).thenReturn(null)

        assertThrows<IllegalArgumentException> {
            handler.handle(session, command)
        }
    }
}
