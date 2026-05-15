package com.aau.server

import com.aau.saboteur.model.Player
import com.aau.server.model.LobbyEntity
import com.aau.server.repository.GameRepository
import com.aau.server.repository.LobbyRepository
import com.aau.server.service.*
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
        // Add 9 more players
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
    }

    @Test
    fun `loadFromDb restores state correctly`() {
        val players = listOf(Player("p1", "Alice"))
        val entity = LobbyEntity("1234", "p1", false, objectMapper.writeValueAsString(players), System.currentTimeMillis())
        
        whenever(lobbyRepository.findAll()).thenReturn(listOf(entity))
        
        lobbyService.loadFromDb()
        
        val lobby = lobbyService.getLobby("1234")
        assertEquals("1234", lobby.lobbyCode)
        assertEquals(1, lobby.players.size)
    }
}
