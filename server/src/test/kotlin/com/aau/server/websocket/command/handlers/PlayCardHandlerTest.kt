package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.GameState
import com.aau.server.model.TurnResult
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.PlayCardCommand
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.locks.ReentrantLock

class PlayCardHandlerTest {

    private val messagingService: MessagingService = mock()
    private val turnManager: TurnManager = mock()
    private val lobbyService: LobbyService = mock()
    private val session: WebSocketSession = mock()
    private lateinit var handler: PlayCardHandler

    @BeforeEach
    fun setUp() {
        handler = PlayCardHandler(messagingService, turnManager, lobbyService)
        whenever(session.id).thenReturn("session-1")
    }

    @Test
    fun `handle play card successfully`() {
        val lobbyCode = "1234"
        val playerId = "player-1"
        val command = PlayCardCommand(playerId, "card-1", BoardPosition(0, 0), false)
        val turnResult = TurnResult(GameState(emptyList(), "next-player"), emptyMap(), null)

        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(lobbyCode)
        whenever(messagingService.getLobbyLock(lobbyCode)).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(playerId)
        whenever(turnManager.playCard(eq(lobbyCode), eq(playerId), eq("card-1"), any(), eq(false))).thenReturn(turnResult)

        handler.handle(session, command)

        verify(turnManager).playCard(eq(lobbyCode), eq(playerId), eq("card-1"), any(), eq(false))
        verify(messagingService, times(2)).sendEventToLobby(eq(lobbyCode), any())
    }

    @Test
    fun `handle play card with winner cleans up lobby`() {
        val lobbyCode = "1234"
        val playerId = "player-1"
        val command = PlayCardCommand(playerId, "card-1", BoardPosition(0, 0), false)
        val turnResult = TurnResult(GameState(emptyList(), "next-player"), emptyMap(), "DWARVES")

        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(lobbyCode)
        whenever(messagingService.getLobbyLock(lobbyCode)).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(playerId)
        whenever(turnManager.playCard(eq(lobbyCode), eq(playerId), eq("card-1"), any(), eq(false))).thenReturn(turnResult)

        handler.handle(session, command)

        verify(messagingService, times(3)).sendEventToLobby(eq(lobbyCode), any())
        verify(lobbyService).deleteLobbyInternal(eq(lobbyCode), any())
    }

    @Test
    fun `handle play card throws exception if playerId mismatch`() {
        val lobbyCode = "1234"
        val playerId = "player-1"
        val command = PlayCardCommand("other-player", "card-1", BoardPosition(0, 0), false)

        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(lobbyCode)
        whenever(messagingService.getLobbyLock(lobbyCode)).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(playerId)

        assertThrows<IllegalArgumentException> {
            handler.handle(session, command)
        }
    }
}
