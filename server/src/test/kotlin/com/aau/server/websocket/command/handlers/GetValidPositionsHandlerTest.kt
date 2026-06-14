package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.*
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.GetValidPositionsCommand
import com.aau.server.websocket.event.GameEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession

class GetValidPositionsHandlerTest {

    private val messagingService: MessagingService = mock()
    private val turnManager: TurnManager = mock()
    private val session: WebSocketSession = mock()
    private lateinit var handler: GetValidPositionsHandler

    @BeforeEach
    fun setUp() {
        handler = GetValidPositionsHandler(messagingService, turnManager)
        whenever(session.id).thenReturn("session-1")
    }

    @Test
    fun `handle get valid positions successfully`() {
        val lobbyCode = "1234"
        val playerId = "player-1"
        val command = GetValidPositionsCommand("card-1", false)
        val card = TunnelCard("card-1", CardType.PATH, setOf(Direction.TOP, Direction.BOTTOM))
        val gameState = GameState(emptyList(), "player-1", emptyList())
        val validPositions = listOf(BoardPosition(0, 0))

        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(lobbyCode)
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(playerId)
        whenever(turnManager.getHands(lobbyCode)).thenReturn(mapOf(playerId to listOf(card)))
        whenever(turnManager.getGameState(lobbyCode)).thenReturn(gameState)
        whenever(turnManager.getValidPositions(card, false, emptyList())).thenReturn(validPositions)

        handler.handle(session, command)

        verify(messagingService).sendEventToSession(eq("session-1"), any<GameEvent.ValidPositions>())
    }

    @Test
    fun `handle get valid positions throws exception if card not in hand`() {
        val lobbyCode = "1234"
        val playerId = "player-1"
        val command = GetValidPositionsCommand("other-card", false)

        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(lobbyCode)
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(playerId)
        whenever(turnManager.getHands(lobbyCode)).thenReturn(mapOf(playerId to emptyList()))

        assertThrows<IllegalArgumentException> {
            handler.handle(session, command)
        }
    }
}
