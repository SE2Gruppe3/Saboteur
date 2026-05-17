package com.aau.server.service

import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.GameState
import com.aau.server.model.CardDistributionResult
import com.aau.server.model.GameStartResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.concurrent.locks.ReentrantLock

class GameLifecycleServiceTest {

    private val lobbyService: LobbyService = mock()
    private val gameService: GameService = mock()
    private val turnManager: TurnManager = mock()
    private val messagingService: MessagingService = mock()
    private lateinit var gameLifecycleService: GameLifecycleService

    @BeforeEach
    fun setUp() {
        gameLifecycleService = GameLifecycleService(lobbyService, gameService, turnManager, messagingService)
    }

    @Test
    fun `startGame successful orchestration`() {
        val lobbyCode = "1234"
        val hostId = "p1"
        val players = listOf(Player("p1", "Alice"), Player("p2", "Bob"), Player("p3", "Charlie"))
        val lobby = LobbyState(lobbyCode, hostId, players, false)
        val startedLobby = lobby.copy(gameStarted = true)
        
        val gameStartResult = GameStartResult(
            gameState = GameState(emptyList(), "p1"),
            playerRoles = mapOf("p1" to players[0], "p2" to players[1], "p3" to players[2]),
            cardDistribution = CardDistributionResult(emptyMap(), emptyList(), emptyList(), mock())
        )

        whenever(lobbyService.getLobby(lobbyCode)).thenReturn(lobby)
        whenever(gameService.startGame(players)).thenReturn(gameStartResult)
        whenever(lobbyService.markGameStarted(lobbyCode)).thenReturn(startedLobby)
        whenever(lobbyService.getAllLobbies()).thenReturn(listOf(startedLobby))

        gameLifecycleService.startGame(lobbyCode, hostId, players)

        verify(gameService).setPlayerData(eq(lobbyCode), any())
        verify(turnManager).initializeGame(eq(lobbyCode), any(), any())
        verify(lobbyService).markGameStarted(lobbyCode)
        
        // Verify broadcast events
        verify(messagingService, atLeastOnce()).sendEventToLobby(eq(lobbyCode), any())
        verify(messagingService, times(3)).sendEventToPlayer(any(), any())
        verify(messagingService).broadcastEvent(any())
    }

    @Test
    fun `startGame throws if not host`() {
        val lobbyCode = "1234"
        val players = listOf(Player("p1", "Alice"))
        val lobby = LobbyState(lobbyCode, "p1", players, false)

        whenever(lobbyService.getLobby(lobbyCode)).thenReturn(lobby)

        assertThrows<IllegalArgumentException> {
            gameLifecycleService.startGame(lobbyCode, "not-host", players)
        }
    }

    @Test
    fun `startGame throws if game already started`() {
        val lobbyCode = "1234"
        val players = listOf(Player("p1", "Alice"))
        val lobby = LobbyState(lobbyCode, "p1", players, true)

        whenever(lobbyService.getLobby(lobbyCode)).thenReturn(lobby)

        assertThrows<IllegalArgumentException> {
            gameLifecycleService.startGame(lobbyCode, "p1", players)
        }
    }
}
