package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.LobbyState
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.HeartbeatCommand
import com.aau.server.websocket.command.LobbyListFetchCommand
import com.aau.server.websocket.command.SyncAckCommand
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession

class SimpleHandlersTest {

    private val messagingService: MessagingService = mock()
    private val lobbyService: LobbyService = mock()
    private val session: WebSocketSession = mock()

    @BeforeEach
    fun setUp() {
        whenever(session.id).doReturn("session-1")
    }

    @Test
    fun `HeartbeatHandler updates activity`() {
        val handler = HeartbeatHandler(messagingService, lobbyService)
        val command = HeartbeatCommand("p1", "L1")
        
        handler.handle(session, command)
        
        verify(messagingService).updatePlayerActivity("p1")
        verify(lobbyService).updateActivity("L1")
    }

    @Test
    fun `LobbyListFetchHandler sends lobby list`() {
        val handler = LobbyListFetchHandler(messagingService, lobbyService)
        val command = LobbyListFetchCommand()
        val lobbies = listOf(LobbyState("L1", "h1"))
        
        whenever(lobbyService.getAllLobbies()).doReturn(lobbies)
        
        handler.handle(session, command)
        
        verify(messagingService).sendEventToSession(eq("session-1"), any())
    }

    @Test
    fun `SyncAckHandler sets session synced`() {
        val handler = SyncAckHandler(messagingService)
        val command = SyncAckCommand()
        
        handler.handle(session, command)
        
        verify(messagingService).setSessionSynced("session-1")
    }
}
