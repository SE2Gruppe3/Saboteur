package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.LobbyCreateCommand
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession

class LobbyCreateHandlerTest {

    private val messagingService: MessagingService = mock()
    private val lobbyService: LobbyService = mock()
    private val session: WebSocketSession = mock()
    private lateinit var handler: LobbyCreateHandler

    @BeforeEach
    fun setUp() {
        handler = LobbyCreateHandler(messagingService, lobbyService)
        whenever(session.id).thenReturn("session-1")
    }

    @Test
    fun `handle lobby create successfully`() {
        val playerName = "Alice"
        val command = LobbyCreateCommand(playerName)
        val lobbyState = LobbyState("1234", "p1", listOf(Player("p1", playerName)), false)

        // Mock with correct parameters for the new LobbyService.createLobby signature
        whenever(lobbyService.createLobby(eq(playerName), anyOrNull(), any(), any())).thenReturn(lobbyState)

        handler.handle(session, command)

        verify(lobbyService).createLobby(eq(playerName), anyOrNull(), any(), any())
        verify(messagingService).registerPlayer("session-1", "p1")
        verify(messagingService).joinLobbyGroup("session-1", "1234")
    }
}
