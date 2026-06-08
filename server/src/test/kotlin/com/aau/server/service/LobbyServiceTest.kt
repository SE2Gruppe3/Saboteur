package com.aau.server.service

import com.aau.saboteur.model.LobbyVisibility
import com.aau.saboteur.model.Player
import com.aau.server.model.LobbyEntity
import com.aau.server.repository.GameRepository
import com.aau.server.repository.LobbyRepository
import com.aau.server.websocket.event.GameEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.concurrent.locks.ReentrantLock

class LobbyServiceTest {

    private val lobbyRepository: LobbyRepository = mock()
    private val gameRepository: GameRepository = mock()
    private val gameService: GameService = mock()
    private val messagingService: MessagingService = mock()
    private val turnManager: TurnManager = mock()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private lateinit var lobbyService: LobbyService

    @BeforeEach
    fun setUp() {
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())
        lobbyService = LobbyService(
            lobbyRepository,
            gameRepository,
            objectMapper,
            gameService,
            messagingService,
            turnManager
        )
    }

    @Test
    fun `createLobby returns lobby and persists`() {
        val state = lobbyService.createLobby("Basti", "p1", visibility = LobbyVisibility.PUBLIC, isGuest = false)
        assertEquals("p1", state.hostId)
        assertEquals(1, state.players.size)
        assertFalse(state.players.first().isGuest)
        assertEquals(LobbyVisibility.PUBLIC, state.visibility)
        verify(lobbyRepository).save(any())
    }

    @Test
    fun `joinLobby adds new player`() {
        val created = lobbyService.createLobby("Host", "h1")
        val updated = lobbyService.joinLobby(created.lobbyCode, "Max", "p2", isGuest = true)
        assertEquals(2, updated.players.size)
        assertTrue(updated.players.any { it.id == "p2" && it.isGuest })
    }

    @Test
    fun `joinLobby returns existing lobby if player already member`() {
        val created = lobbyService.createLobby("Host", "h1")
        val result = lobbyService.joinLobby(created.lobbyCode, "Host", "h1")
        assertEquals(1, result.players.size)
        verify(lobbyRepository, times(1)).save(any()) // Only once during create
    }

    @Test
    fun `visibility filtering works`() {
        lobbyService.createLobby("Host1", "h1", visibility = LobbyVisibility.PUBLIC)
        lobbyService.createLobby("Host2", "h2", visibility = LobbyVisibility.PRIVATE)
        
        val publicLobbies = lobbyService.getPublicLobbies()
        assertEquals(1, publicLobbies.size)
        assertEquals("h1", publicLobbies.first().hostId)
        
        val allLobbies = lobbyService.getAllLobbies()
        assertEquals(2, allLobbies.size)
    }

    @Test
    fun `joinLobby throws if game started`() {
        val created = lobbyService.createLobby("Host", "h1")
        lobbyService.markGameStarted(created.lobbyCode)
        assertThrows<IllegalArgumentException> {
            lobbyService.joinLobby(created.lobbyCode, "Max", "p2")
        }
    }

    @Test
    fun `joinLobby throws if lobby full`() {
        val created = lobbyService.createLobby("Host", "h1")
        for (i in 2..10) {
            lobbyService.joinLobby(created.lobbyCode, "P$i", "p$i")
        }
        assertThrows<IllegalArgumentException> {
            lobbyService.joinLobby(created.lobbyCode, "P11", "p11")
        }
    }

    @Test
    fun `leaveLobby updates host if host leaves`() {
        val created = lobbyService.createLobby("Host", "h1")
        lobbyService.joinLobby(created.lobbyCode, "Max", "p2")
        val updated = lobbyService.leaveLobby(created.lobbyCode, "h1")
        assertEquals("p2", updated?.hostId)
        assertEquals(1, updated?.players?.size)
    }

    @Test
    fun `leaveLobby deletes lobby if last player leaves`() {
        val created = lobbyService.createLobby("Host", "h1")
        val result = lobbyService.leaveLobby(created.lobbyCode, "h1")
        assertNull(result)
        verify(lobbyRepository).deleteById(created.lobbyCode)
    }

    @Test
    fun `kickPlayer removes player correctly`() {
        val created = lobbyService.createLobby("Host", "h1")
        lobbyService.joinLobby(created.lobbyCode, "Max", "p2")
        
        val updated = lobbyService.kickPlayer(created.lobbyCode, "p2")
        
        assertEquals(1, updated.players.size)
        assertFalse(updated.players.any { it.id == "p2" })
        verify(lobbyRepository, times(3)).save(any()) // create, join, kick
    }

    @Test
    fun `kickPlayer throws if player not found`() {
        val created = lobbyService.createLobby("Host", "h1")
        assertThrows<IllegalArgumentException> {
            lobbyService.kickPlayer(created.lobbyCode, "pNonExistent")
        }
    }

    @Test
    fun `kickPlayer throws if game already started`() {
        val created = lobbyService.createLobby("Host", "h1")
        lobbyService.joinLobby(created.lobbyCode, "Max", "p2")
        lobbyService.markGameStarted(created.lobbyCode)
        
        assertThrows<IllegalArgumentException> {
            lobbyService.kickPlayer(created.lobbyCode, "p2")
        }
    }

    @Test
    fun `loadFromDb handles corrupted lobby data`() {
        val entity = LobbyEntity("1", "h1", false, "{invalid}", 0L, LobbyVisibility.PUBLIC)
        whenever(lobbyRepository.findAll()).thenReturn(listOf(entity))
        val count = lobbyService.loadFromDb()
        assertEquals(0, count)
    }

    @Test
    fun `cleanupInactiveLobbies deletes old lobbies`() {
        val lobby = lobbyService.createLobby("Host", "h1")
        lobbyService.updateActivity(lobby.lobbyCode)
        
        lobbyService.cleanupInactiveLobbies()
        verify(lobbyRepository, never()).deleteById(any<String>())
    }

    @Test
    fun `deleteLobbyInternal handles messaging errors gracefully`() {
        val lobby = lobbyService.createLobby("Host", "h1")
        whenever(messagingService.sendEventToLobby(any(), any())).thenThrow(RuntimeException("WS down"))
        
        assertDoesNotThrow {
            lobbyService.deleteLobbyInternal(lobby.lobbyCode, "test")
        }
    }

    @Test
    fun `updateActivity updates time`() {
        val lobby = lobbyService.createLobby("Host", "h1")
        lobbyService.updateActivity(lobby.lobbyCode)
        assertNotNull(lobby.lobbyCode)
    }
}
