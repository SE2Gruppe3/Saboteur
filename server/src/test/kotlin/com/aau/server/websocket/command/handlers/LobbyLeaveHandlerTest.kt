package com.aau.server.websocket.command.handlers

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.LobbyLeaveCommand
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.locks.ReentrantLock

class LobbyLeaveHandlerTest {

    private val messagingService: MessagingService = mock()
    private val lobbyService: LobbyService = mock()
    private val session: WebSocketSession = mock()
    private lateinit var handler: LobbyLeaveHandler

    @BeforeEach
    fun setUp() {
        handler = LobbyLeaveHandler(messagingService, lobbyService)
        whenever(session.id).thenReturn("session-1")
    }

    @Test
    fun `handle lobby leave successfully`() {
        val lobbyCode = "1234"
        val playerId = "p1"
        val command = LobbyLeaveCommand(lobbyCode, playerId)

        whenever(messagingService.getLobbyLock(lobbyCode)).thenReturn(ReentrantLock())

        handler.handle(session, command)

        verify(lobbyService).leaveLobby(lobbyCode, playerId)
        verify(messagingService).leaveLobbyGroup("session-1", lobbyCode)
        verify(messagingService).sendEventToSession(eq("session-1"), any())
    }
}
