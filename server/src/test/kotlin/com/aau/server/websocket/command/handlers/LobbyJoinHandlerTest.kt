package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.LobbyJoinCommand
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession

class LobbyJoinHandlerTest {

    private val messagingService: MessagingService = mock()
    private val lobbyService: LobbyService = mock()
    private val session: WebSocketSession = mock()
    private lateinit var handler: LobbyJoinHandler

    @BeforeEach
    fun setUp() {
        handler = LobbyJoinHandler(messagingService, lobbyService)
        whenever(session.id).thenReturn("session-1")
    }

    @Test
    fun `handle lobby join successfully`() {
        val lobbyCode = "1234"
        val playerName = "Bob"
        val command = LobbyJoinCommand(lobbyCode, playerName)
        val player = Player("p2", playerName)
        val lobbyState = LobbyState(lobbyCode, "p1", listOf(Player("p1", "Alice"), player), false)

        // Using explicit values instead of any() for precision
        whenever(lobbyService.joinLobby(eq(lobbyCode), eq(playerName), isNull(), eq(true))).thenReturn(lobbyState)

        handler.handle(session, command)

        verify(lobbyService).joinLobby(eq(lobbyCode), eq(playerName), isNull(), eq(true))
        verify(messagingService).registerPlayer("session-1", "p2")
        verify(messagingService).joinLobbyGroup("session-1", lobbyCode)
    }
}
