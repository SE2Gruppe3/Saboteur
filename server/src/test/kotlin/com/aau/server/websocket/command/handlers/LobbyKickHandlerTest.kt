package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.LobbyVisibility
import com.aau.saboteur.model.Player
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.LobbyKickCommand
import com.aau.server.websocket.event.GameEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.locks.ReentrantLock

class LobbyKickHandlerTest {

    private val messagingService: MessagingService = mock()
    private val lobbyService: LobbyService = mock()
    private val session: WebSocketSession = mock()
    private lateinit var handler: LobbyKickHandler

    @BeforeEach
    fun setUp() {
        handler = LobbyKickHandler(messagingService, lobbyService)
        whenever(session.id).thenReturn("session-host")
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())
    }

    @Test
    fun `handle kick successfully as host`() {
        val lobbyCode = "1234"
        val hostId = "host-1"
        val targetId = "target-1"
        val command = LobbyKickCommand(lobbyCode, hostId, targetId)
        val lobby = LobbyState(lobbyCode, hostId, listOf(Player(hostId, "Host"), Player(targetId, "Target")))

        whenever(lobbyService.getLobby(lobbyCode)).thenReturn(lobby)

        handler.handle(session, command)

        verify(lobbyService).kickPlayer(lobbyCode, targetId)
        verify(messagingService).sendEventToPlayer(eq(targetId), any<GameEvent.PlayerKicked>())
    }

    @Test
    fun `handle kick fails if requester is not host`() {
        val lobbyCode = "1234"
        val hostId = "real-host"
        val impostorId = "impostor"
        val targetId = "target-1"
        val command = LobbyKickCommand(lobbyCode, impostorId, targetId)
        val lobby = LobbyState(lobbyCode, hostId, listOf(Player(hostId, "Host"), Player(targetId, "Target")))

        whenever(lobbyService.getLobby(lobbyCode)).thenReturn(lobby)

        handler.handle(session, command)

        verify(lobbyService, never()).kickPlayer(any(), any())
        verify(messagingService).sendEventToSession(eq("session-host"), any<GameEvent.ErrorEvent>())
    }

    @Test
    fun `handle kick fails if host tries to kick themselves`() {
        val lobbyCode = "1234"
        val hostId = "host-1"
        val command = LobbyKickCommand(lobbyCode, hostId, hostId)
        val lobby = LobbyState(lobbyCode, hostId, listOf(Player(hostId, "Host")))

        whenever(lobbyService.getLobby(lobbyCode)).thenReturn(lobby)

        handler.handle(session, command)

        verify(lobbyService, never()).kickPlayer(any(), any())
        verify(messagingService).sendEventToSession(eq("session-host"), any<GameEvent.ErrorEvent>())
    }

    @Test
    fun `handle kick fails if game has already started`() {
        val lobbyCode = "1234"
        val hostId = "host-1"
        val targetId = "target-1"
        val command = LobbyKickCommand(lobbyCode, hostId, targetId)
        val lobby = LobbyState(lobbyCode, hostId, listOf(Player(hostId, "Host"), Player(targetId, "Target")))

        whenever(lobbyService.getLobby(lobbyCode)).thenReturn(lobby)
        whenever(lobbyService.kickPlayer(lobbyCode, targetId))
            .thenThrow(IllegalArgumentException("Spieler können nicht während eines laufenden Spiels gekickt werden"))

        handler.handle(session, command)

        verify(messagingService).sendEventToSession(eq("session-host"), any<GameEvent.ErrorEvent>())
        verify(messagingService, never()).sendEventToPlayer(any(), any())
    }
}
