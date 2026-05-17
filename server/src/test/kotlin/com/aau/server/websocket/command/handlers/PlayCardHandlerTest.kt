package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.MapResult
import com.aau.saboteur.model.ToolType
import com.aau.server.model.TurnResult
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.locks.ReentrantLock

class PlayCardHandlerTest {

    // Gemeinsame Mocks
    private val messagingService: MessagingService = mock()
    private val turnManager: TurnManager = mock()
    private val lobbyService: LobbyService = mock()
    private val session: WebSocketSession = mock()
    private lateinit var playCardHandler: PlayCardHandler

    @BeforeEach
    fun setUp() {
        playCardHandler = PlayCardHandler(messagingService, turnManager, lobbyService)
        whenever(session.id).thenReturn("session-1")
    }

    // --- PlayCardHandler TESTS ---
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

        playCardHandler.handle(session, command)

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

        playCardHandler.handle(session, command)

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
        assertThrows<IllegalArgumentException> { playCardHandler.handle(session, command) }
    }

    // --- PlayBlockCardHandler TESTS ---
    @Test
    fun `play block card throws if session not in lobby`() {
        val blockHandler = PlayBlockCardHandler(messagingService, turnManager)
        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(null)
        val cmd = PlayBlockCardCommand("p1", "c1", "p2")
        assertThrows<IllegalArgumentException> {
            blockHandler.handle(session, cmd)
        }
    }

    @Test
    fun `play block card throws if session not linked to player`() {
        val blockHandler = PlayBlockCardHandler(messagingService, turnManager)
        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn("lobby55")
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn(null)
        val cmd = PlayBlockCardCommand("p1", "c1", "p2")
        assertThrows<IllegalArgumentException> { blockHandler.handle(session, cmd) }
    }

    @Test
    fun `play block card throws on playerId mismatch`() {
        val blockHandler = PlayBlockCardHandler(messagingService, turnManager)
        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn("lobby55")
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn("xWrongId")
        val cmd = PlayBlockCardCommand("p1", "c1", "p2")
        assertThrows<IllegalArgumentException> { blockHandler.handle(session, cmd) }
    }

    @Test
    fun `play block card calls turnManager and sends events for happy path`() {
        val blockHandler = PlayBlockCardHandler(messagingService, turnManager)
        whenever(messagingService.getLobbyCodeForSession(any())).thenReturn("lobby55")
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession(any())).thenReturn("p1")
        val cmd = PlayBlockCardCommand("p1", "c1", "p2")
        val gameStateMock = mock<GameState>()
        val hands = mapOf("p1" to emptyList<com.aau.saboteur.model.TunnelCard>())
        whenever(turnManager.playBlockCard(any(), any(), any(), any()))
            .thenReturn(TurnResult(gameStateMock, hands, null))

        blockHandler.handle(session, cmd)

        verify(turnManager).playBlockCard("lobby55", "p1", "c1", "p2")
        verify(messagingService, times(2)).sendEventToLobby(eq("lobby55"), any())
    }

    // --- PlayMapCardHandler TESTS ---
    @Test
    fun `play map card happy path calls turnManager and sends events`() {
        val mapHandler = PlayMapCardHandler(messagingService, turnManager)
        whenever(messagingService.getLobbyCodeForSession(any())).thenReturn("lobbyMC")
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession(any())).thenReturn("p1")
        val cmd = PlayMapCardCommand("p1", "card99", BoardPosition(1, 1))
        // FIX: Real dummy TurnResult to avoid NPE!
        whenever(turnManager.playMapCard(any(), any(), any(), any()))
            .thenReturn(Pair(
                TurnResult(GameState(emptyList(), "dummy"), emptyMap(), null),
                mock<MapResult>()
            ))
        mapHandler.handle(session, cmd)
        verify(turnManager).playMapCard(eq("lobbyMC"), eq("p1"), eq("card99"), any())
        verify(messagingService, times(2)).sendEventToLobby(eq("lobbyMC"), any())
    }

    @Test
    fun `play map card throws if not in lobby`() {
        val mapHandler = PlayMapCardHandler(messagingService, turnManager)
        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(null)
        val cmd = PlayMapCardCommand("p1", "c1", BoardPosition(1, 1))
        assertThrows<IllegalArgumentException> { mapHandler.handle(session, cmd) }
    }

    @Test
    fun `play map card throws on playerId mismatch`() {
        val mapHandler = PlayMapCardHandler(messagingService, turnManager)
        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn("LBX")
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn("someOtherId")
        val cmd = PlayMapCardCommand("p1", "c1", BoardPosition(1, 1))
        assertThrows<IllegalArgumentException> { mapHandler.handle(session, cmd) }
    }

    // --- PlayRepairCardHandler TESTS ---
    @Test
    fun `play repair card happy path calls turnManager and sends events`() {
        val repairHandler = PlayRepairCardHandler(messagingService, turnManager)
        whenever(messagingService.getLobbyCodeForSession(any())).thenReturn("replobby")
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession(any())).thenReturn("p1")
        val cmd = PlayRepairCardCommand("p1", "card666", "p2", ToolType.LANTERN)
        whenever(turnManager.playRepairCard(any(), any(), any(), any(), any()))
            .thenReturn(TurnResult(mock<GameState>(), emptyMap(), null))
        repairHandler.handle(session, cmd)
        verify(turnManager).playRepairCard(eq("replobby"), eq("p1"), eq("card666"), eq("p2"), eq(ToolType.LANTERN))
        verify(messagingService, times(2)).sendEventToLobby(eq("replobby"), any())
    }

    @Test
    fun `play repair card throws if not in lobby`() {
        val repairHandler = PlayRepairCardHandler(messagingService, turnManager)
        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(null)
        val cmd = PlayRepairCardCommand("p1", "c1", "p2", ToolType.LANTERN)
        assertThrows<IllegalArgumentException> { repairHandler.handle(session, cmd) }
    }
    @Test
    fun `play repair card throws on playerId mismatch`() {
        val repairHandler = PlayRepairCardHandler(messagingService, turnManager)
        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn("LBY")
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn("somethingelse")
        val cmd = PlayRepairCardCommand("p1", "c1", "p2", ToolType.LANTERN)
        assertThrows<IllegalArgumentException> { repairHandler.handle(session, cmd) }
    }

    // --- PlayRockfallCardHandler TESTS ---
    @Test
    fun `play rockfall card happy path calls turnManager and sends events`() {
        val rockfallHandler = PlayRockfallCardHandler(messagingService, turnManager)
        whenever(messagingService.getLobbyCodeForSession(any())).thenReturn("rocky")
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession(any())).thenReturn("p1")
        val cmd = PlayRockfallCardCommand("p1", "rf1", BoardPosition(4, 2))
        whenever(turnManager.playRockfallCard(any(), any(), any(), any()))
            .thenReturn(TurnResult(mock<GameState>(), emptyMap(), null))
        rockfallHandler.handle(session, cmd)
        verify(turnManager).playRockfallCard(eq("rocky"), eq("p1"), eq("rf1"), any())
        verify(messagingService, times(2)).sendEventToLobby(eq("rocky"), any())
    }

    @Test
    fun `play rockfall card throws if not in lobby`() {
        val rockfallHandler = PlayRockfallCardHandler(messagingService, turnManager)
        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn(null)
        val cmd = PlayRockfallCardCommand("p1", "c1", BoardPosition(2, 3))
        assertThrows<IllegalArgumentException> { rockfallHandler.handle(session, cmd) }
    }

    @Test
    fun `play rockfall card throws on playerId mismatch`() {
        val rockfallHandler = PlayRockfallCardHandler(messagingService, turnManager)
        whenever(messagingService.getLobbyCodeForSession("session-1")).thenReturn("ROCKL")
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())
        whenever(messagingService.getPlayerIdForSession("session-1")).thenReturn("wrong")
        val cmd = PlayRockfallCardCommand("p1", "c1", BoardPosition(2, 3))
        assertThrows<IllegalArgumentException> { rockfallHandler.handle(session, cmd) }
    }
}