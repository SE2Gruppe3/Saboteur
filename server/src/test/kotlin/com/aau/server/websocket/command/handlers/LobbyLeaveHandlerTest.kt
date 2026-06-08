package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.LobbyVisibility
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.LobbyLeaveCommand
import org.junit.jupiter.api.Assertions.assertThrows
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

    private val lobbyCode = "1234"
    private val hostId = "host"
    private val otherPlayerId = "p2"

    private val lobby = LobbyState(
        lobbyCode = lobbyCode,
        hostId = hostId,
        players = emptyList(),
        gameStarted = false,
        visibility = LobbyVisibility.PUBLIC
    )

    @BeforeEach
    fun setUp() {
        handler = LobbyLeaveHandler(messagingService, lobbyService)
        whenever(session.id).thenReturn("session-1")
        whenever(messagingService.getLobbyLock(lobbyCode)).thenReturn(ReentrantLock())
        whenever(lobbyService.getLobby(lobbyCode)).thenReturn(lobby)
    }

    @Test
    fun `player leaving their own lobby is allowed`() {
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(hostId)
        val command = LobbyLeaveCommand(lobbyCode, hostId)

        handler.handle(session, command)

        verify(lobbyService).leaveLobby(lobbyCode, hostId)
        verify(messagingService).leaveLobbyGroup("session-1", lobbyCode)
        verify(messagingService).sendEventToSession(eq("session-1"), any())
    }

    @Test
    fun `host kicking another player is allowed`() {
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(hostId)
        val command = LobbyLeaveCommand(lobbyCode, otherPlayerId)

        handler.handle(session, command)

        verify(lobbyService).leaveLobby(lobbyCode, otherPlayerId)
        verify(messagingService).leaveLobbyGroup("session-1", lobbyCode)
        verify(messagingService).sendEventToSession(eq("session-1"), any())
    }

    @Test
    fun `non-host trying to remove another player throws IllegalArgumentException`() {
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(otherPlayerId)
        val command = LobbyLeaveCommand(lobbyCode, hostId)

        assertThrows(IllegalArgumentException::class.java) {
            handler.handle(session, command)
        }

        verify(lobbyService, never()).leaveLobby(any(), any())
    }
}
