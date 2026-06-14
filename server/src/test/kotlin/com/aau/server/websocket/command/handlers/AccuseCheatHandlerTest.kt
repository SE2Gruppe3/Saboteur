package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.CheatAccusationResult
import com.aau.saboteur.model.CheatType
import com.aau.saboteur.model.GameState
import com.aau.server.model.CheatAccusationTurnResult
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.AccuseCheatCommand
import com.aau.server.websocket.event.GameEvent
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.locks.ReentrantLock

class AccuseCheatHandlerTest {

    private val messagingService: MessagingService = mock()
    private val turnManager: TurnManager = mock()
    private val session: WebSocketSession = mock()
    private lateinit var handler: AccuseCheatHandler

    @BeforeEach
    fun setUp() {
        handler = AccuseCheatHandler(messagingService, turnManager)
        whenever(session.id).thenReturn("session-123")
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())
    }

    @Test
    fun `handle resolves accusation for session player and broadcasts result`() {
        val lobbyCode = "L1"
        val accuserId = "p1"
        val accusedId = "p2"
        val accusation = CheatAccusationResult(
            accuserPlayerId = accuserId,
            accusedPlayerId = accusedId,
            caught = true,
            cheatType = CheatType.VOLUME_SEQUENCE_DISCARD
        )
        val result = CheatAccusationTurnResult(
            accusation = accusation,
            updatedGameState = GameState(deckSize = 3),
            updatedHands = emptyMap()
        )

        whenever(messagingService.getPlayerIdForSession("session-123")).thenReturn(accuserId)
        whenever(turnManager.accuseCheating(lobbyCode, accuserId, accusedId)).thenReturn(result)

        handler.handle(session, AccuseCheatCommand(lobbyCode, accusedId))

        verify(turnManager).accuseCheating(lobbyCode, accuserId, accusedId)
        verify(messagingService).sendEventToLobby(lobbyCode, GameEvent.GameStateUpdate(result.updatedGameState))
        verify(messagingService).sendEventToLobby(lobbyCode, GameEvent.CardsDealt(result.updatedHands))
        verify(messagingService).sendEventToLobby(lobbyCode, GameEvent.CheatAccusationResultEvent(accusation))
    }

    @Test
    fun `handle throws when session has no playerId`() {
        whenever(messagingService.getPlayerIdForSession("session-123")).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) {
            handler.handle(session, AccuseCheatCommand("L1", "p2"))
        }

        verify(turnManager, never()).accuseCheating(any(), any(), any())
    }
}
