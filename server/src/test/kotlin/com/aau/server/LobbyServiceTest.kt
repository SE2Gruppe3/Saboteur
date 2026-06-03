package com.aau.server

import com.aau.saboteur.model.Player
import com.aau.server.model.LobbyEntity
import com.aau.server.repository.GameRepository
import com.aau.server.repository.LobbyRepository
import com.aau.server.service.GameService
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
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
    fun `createLobby returns lobby with host as first player and broadcasts`() {
        val state = lobbyService.createLobby("Basti", "p1")

        assertEquals("p1", state.hostId)
        assertEquals(1, state.players.size)
        assertEquals("Basti", state.players.first().name)

        verify(lobbyRepository).save(any())
        verify(messagingService).sendEventToLobby(any(), any())
        verify(messagingService).broadcastEvent(any())
    }

    @Test
    fun `joinLobby adds a new player and broadcasts`() {
        val created = lobbyService.createLobby("Host", "h1")
        reset(messagingService, lobbyRepository)
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())

        val updated = lobbyService.joinLobby(created.lobbyCode, "Max", "p2")

        assertEquals(2, updated.players.size)
        verify(messagingService, atLeastOnce()).broadcastEvent(any())
        verify(messagingService, atLeastOnce()).sendEventToLobby(any(), any())
    }

    @Test
    fun `joinLobby throws if lobby full`() {
        val created = lobbyService.createLobby("Host", "h1")
        for (i in 2..10) {
            lobbyService.joinLobby(created.lobbyCode, "Player$i", "p$i")
        }

        assertThrows<IllegalArgumentException> {
            lobbyService.joinLobby(created.lobbyCode, "FullPlayer", "p11")
        }
    }

    @Test
    fun `joinLobby throws if game already started`() {
        val created = lobbyService.createLobby("Host", "h1")
        lobbyService.markGameStarted(created.lobbyCode)

        assertThrows<IllegalArgumentException> {
            lobbyService.joinLobby(created.lobbyCode, "LatePlayer", "p2")
        }
    }

    @Test
    fun `joinLobby throws when lobby does not exist`() {
        whenever(messagingService.getLobbyLock("MISSING")).thenReturn(ReentrantLock())

        assertThrows<IllegalArgumentException> {
            lobbyService.joinLobby("MISSING", "Alice")
        }
    }

    @Test
    fun `joinLobby returns existing lobby when player id already exists`() {
        val created = lobbyService.createLobby("Host", "p1")
        reset(messagingService, lobbyRepository)
        whenever(messagingService.getLobbyLock(any())).thenReturn(ReentrantLock())

        val result = lobbyService.joinLobby(created.lobbyCode, "Host", "p1")

        assertEquals(1, result.players.size)
        assertEquals("p1", result.players.first().id)
        verify(lobbyRepository, never()).save(any())
    }

    @Test
    fun `leaveLobby removes player and updates host if needed`() {
        val created = lobbyService.createLobby("Host", "h1")
        lobbyService.joinLobby(created.lobbyCode, "Max", "p2")

        val updated = lobbyService.leaveLobby(created.lobbyCode, "h1")

        assertNotNull(updated)
        assertEquals(1, updated!!.players.size)
        assertEquals("p2", updated.hostId)
        assertEquals("Max", updated.players.first().name)
    }

    @Test
    fun `leaveLobby deletes lobby if last player leaves`() {
        val created = lobbyService.createLobby("Host", "h1")

        val updated = lobbyService.leaveLobby(created.lobbyCode, "h1")

        assertNull(updated)
        verify(lobbyRepository).deleteById(created.lobbyCode)
        verify(gameRepository).deleteById(created.lobbyCode)
        verify(turnManager).removeGame(created.lobbyCode)
        verify(gameService).removePlayerData(created.lobbyCode)
        verify(messagingService).clearLobbyMappings(created.lobbyCode)
    }

    @Test
    fun `leaveLobby throws when lobby does not exist`() {
        whenever(messagingService.getLobbyLock("UNKNOWN")).thenReturn(ReentrantLock())

        assertThrows<IllegalArgumentException> {
            lobbyService.leaveLobby("UNKNOWN", "p1")
        }
    }

    @Test
    fun `markGameStarted sets started flag`() {
        val created = lobbyService.createLobby("Host", "h1")

        val updated = lobbyService.markGameStarted(created.lobbyCode)

        assertTrue(updated.gameStarted)
    }

    @Test
    fun `markGameStarted throws when lobby does not exist`() {
        whenever(messagingService.getLobbyLock("NOPE")).thenReturn(ReentrantLock())

        assertThrows<IllegalArgumentException> {
            lobbyService.markGameStarted("NOPE")
        }
    }

    @Test
    fun `getLobby throws when lobby does not exist`() {
        assertThrows<IllegalArgumentException> {
            lobbyService.getLobby("DOES_NOT_EXIST")
        }
    }

    @Test
    fun `getActiveLobbiesCount returns number of active lobbies`() {
        assertEquals(0, lobbyService.getActiveLobbiesCount())

        lobbyService.createLobby("A", "p1")
        lobbyService.createLobby("B", "p2")

        assertEquals(2, lobbyService.getActiveLobbiesCount())
    }

    @Test
    fun `getAllLobbies returns created lobbies`() {
        val lobby1 = lobbyService.createLobby("A", "p1")
        val lobby2 = lobbyService.createLobby("B", "p2")

        val all = lobbyService.getAllLobbies()

        assertEquals(2, all.size)
        assertTrue(all.any { it.lobbyCode == lobby1.lobbyCode })
        assertTrue(all.any { it.lobbyCode == lobby2.lobbyCode })
    }

    @Test
    fun `loadFromDb restores state correctly`() {
        val players = listOf(Player("p1", "Alice"))
        val entity = LobbyEntity(
            "1234",
            "p1",
            false,
            objectMapper.writeValueAsString(players),
            System.currentTimeMillis()
        )

        whenever(lobbyRepository.findAll()).thenReturn(listOf(entity))

        val result = lobbyService.loadFromDb()

        assertEquals(1, result)

        val lobby = lobbyService.getLobby("1234")
        assertEquals("1234", lobby.lobbyCode)
        assertEquals(1, lobby.players.size)
        assertEquals("p1", lobby.hostId)
    }

    @Test
    fun `loadFromDb returns zero when repository is empty`() {
        whenever(lobbyRepository.findAll()).thenReturn(emptyList())

        val result = lobbyService.loadFromDb()

        assertEquals(0, result)
    }

    @Test
    fun `loadFromDb ignores invalid entity json`() {
        val badEntity = LobbyEntity(
            "BAD",
            "host",
            false,
            "not valid json",
            System.currentTimeMillis()
        )

        whenever(lobbyRepository.findAll()).thenReturn(listOf(badEntity))

        val result = lobbyService.loadFromDb()

        assertEquals(0, result)
    }

    @Test
    fun `updateActivity does not throw`() {
        val created = lobbyService.createLobby("Host", "h1")

        lobbyService.updateActivity(created.lobbyCode)

        assertDoesNotThrow {
            lobbyService.updateActivity(created.lobbyCode)
        }
    }

    @Test
    fun `cleanupInactiveLobbies removes empty inactive lobby`() {
        val oldTimestamp = System.currentTimeMillis() - 120000
        val entity = LobbyEntity(
            lobbyCode = "EMPTY_OLD",
            hostId = "host",
            gameStarted = false,
            playersJson = objectMapper.writeValueAsString(emptyList<Player>()),
            lastActivity = oldTimestamp
        )

        whenever(lobbyRepository.findAll()).thenReturn(listOf(entity))
        lobbyService.loadFromDb()

        lobbyService.cleanupInactiveLobbies()

        verify(lobbyRepository).deleteById("EMPTY_OLD")
        verify(gameRepository).deleteById("EMPTY_OLD")
        verify(turnManager).removeGame("EMPTY_OLD")
        verify(gameService).removePlayerData("EMPTY_OLD")
        verify(messagingService).clearLobbyMappings("EMPTY_OLD")
    }
}