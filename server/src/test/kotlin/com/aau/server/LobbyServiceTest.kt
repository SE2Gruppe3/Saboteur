package com.aau.server

import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
import com.aau.server.model.LobbyEntity
import com.aau.server.repository.LobbyRepository
import com.aau.server.service.LobbyService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import java.lang.reflect.Field
import java.util.*

class LobbyServiceTest {

    private lateinit var lobbyRepository: LobbyRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var lobbyService: LobbyService

    @BeforeEach
    fun setUp() {
        lobbyRepository = mock(LobbyRepository::class.java)
        objectMapper = jacksonObjectMapper()
        lobbyService = LobbyService(lobbyRepository, objectMapper)
    }

    @Test
    fun `createLobby returns lobby with host as first player`() {
        val state = lobbyService.createLobby("Basti")

        assertTrue(state.lobbyCode.isNotBlank())
        assertEquals("Basti", state.players.first { it.id == state.hostId }.name)
        assertEquals(1, state.players.size)
        val host = state.players.first()
        assertEquals("Basti", host.name)
        assertEquals(host.id, state.hostId)
        
        verify(lobbyRepository, atLeastOnce()).save(any(LobbyEntity::class.java))
    }

    @Test
    fun `joinLobby adds a new player to existing lobby`() {
        val created = lobbyService.createLobby("Host")

        val updated = lobbyService.joinLobby(created.lobbyCode, "Max")
        val names = updated.players.map { it.name }

        assertEquals(created.lobbyCode, updated.lobbyCode)
        assertTrue(names.contains("Host"))
        assertTrue(names.contains("Max"))
        assertEquals(2, updated.players.size)
        
        verify(lobbyRepository, atLeastOnce()).save(any(LobbyEntity::class.java))
    }

    @Test
    fun `joinLobby unknown code throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            lobbyService.joinLobby("9999", "Max")
        }
    }

    @Test
    fun `joinLobby when full throws`() {
        val created = lobbyService.createLobby("Host")
        repeat(9) {
            lobbyService.joinLobby(created.lobbyCode, "Player $it")
        }
        
        val exception = assertThrows(IllegalArgumentException::class.java) {
            lobbyService.joinLobby(created.lobbyCode, "FullPlayer")
        }
        assertEquals("Lobby is full", exception.message)
    }

    @Test
    fun `joinLobby when game started throws`() {
        val created = lobbyService.createLobby("Host")
        lobbyService.markGameStarted(created.lobbyCode)
        
        val exception = assertThrows(IllegalArgumentException::class.java) {
            lobbyService.joinLobby(created.lobbyCode, "LatePlayer")
        }
        assertEquals("Game has already started", exception.message)
    }

    @Test
    fun `leaveLobby removes player and returns updated lobby`() {
        val created = lobbyService.createLobby("Host")
        val joined = lobbyService.joinLobby(created.lobbyCode, "Player2")
        val p2Id = joined.players.last().id
        
        val updated = lobbyService.leaveLobby(created.lobbyCode, p2Id)
        assertNotNull(updated)
        assertEquals(1, updated!!.players.size)
        assertEquals("Host", updated.players.first().name)
    }

    @Test
    fun `leaveLobby when host leaves assigns new host`() {
        val created = lobbyService.createLobby("Host")
        val hostId = created.hostId
        val joined = lobbyService.joinLobby(created.lobbyCode, "Player2")
        val p2Id = joined.players.last().id
        
        val updated = lobbyService.leaveLobby(created.lobbyCode, hostId)
        assertNotNull(updated)
        assertEquals(1, updated!!.players.size)
        assertEquals(p2Id, updated.hostId)
    }

    @Test
    fun `leaveLobby when last player leaves returns null and removes lobby`() {
        val created = lobbyService.createLobby("Host")
        val updated = lobbyService.leaveLobby(created.lobbyCode, created.hostId)
        
        assertNull(updated)
        assertThrows(IllegalArgumentException::class.java) {
            lobbyService.getLobby(created.lobbyCode)
        }
        verify(lobbyRepository).deleteById(created.lobbyCode)
    }

    @Test
    fun `leaveLobby unknown code throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            lobbyService.leaveLobby("9999", "someId")
        }
    }

    @Test
    fun `markGameStarted sets gameStarted to true`() {
        val created = lobbyService.createLobby("Host")
        val started = lobbyService.markGameStarted(created.lobbyCode)
        assertTrue(started.gameStarted)
        assertTrue(lobbyService.getLobby(created.lobbyCode).gameStarted)
    }

    @Test
    fun `getAllLobbies returns all active lobbies`() {
        lobbyService.createLobby("H1")
        lobbyService.createLobby("H2")
        assertEquals(2, lobbyService.getAllLobbies().size)
    }

    @Test
    fun `getLobby returns correct lobby`() {
        val created = lobbyService.createLobby("Host")
        val fetched = lobbyService.getLobby(created.lobbyCode)
        assertEquals(created.lobbyCode, fetched.lobbyCode)
    }

    @Test
    fun `loadFromDb restores state correctly`() {
        val players = listOf(Player("p1", "Alice"))
        val entity = LobbyEntity("1234", "p1", false, objectMapper.writeValueAsString(players))
        
        `when`(lobbyRepository.findAll()).thenReturn(listOf(entity))
        
        lobbyService.loadFromDb()
        
        val lobby = lobbyService.getLobby("1234")
        assertEquals("1234", lobby.lobbyCode)
        assertEquals("p1", lobby.hostId)
        assertEquals(1, lobby.players.size)
        assertEquals("Alice", lobby.players.first().name)
    }
}
