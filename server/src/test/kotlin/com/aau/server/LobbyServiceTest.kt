package com.aau.server

import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
import com.aau.server.model.LobbyEntity
import com.aau.server.repository.GameRepository
import com.aau.server.repository.LobbyRepository
import com.aau.server.service.*
import com.aau.server.websocket.event.GameEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class LobbyServiceTest {

    private lateinit var lobbyRepository: LobbyRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var gameService: GameService
    private lateinit var messagingService: MessagingService
    private lateinit var turnManager: TurnManager
    private lateinit var objectMapper: ObjectMapper
    private lateinit var lobbyService: LobbyService

    @BeforeEach
    fun setUp() {
        lobbyRepository = mock(LobbyRepository::class.java)
        gameRepository = mock(GameRepository::class.java)
        gameService = mock(GameService::class.java)
        messagingService = mock(MessagingService::class.java)
        turnManager = mock(TurnManager::class.java)
        objectMapper = jacksonObjectMapper()
        
        `when`(messagingService.getLobbyLock(anyString())).thenReturn(ReentrantLock())
        
        lobbyService = LobbyService(
            lobbyRepository, 
            gameRepository, 
            objectMapper, 
            gameService, 
            messagingService, 
            turnManager
        )
    }

    private fun <T> anyK(): T = any<T>() ?: null as T

    @Test
    fun `createLobby returns lobby with host as first player and broadcasts`() {
        val state = lobbyService.createLobby("Basti", "p1")

        assertEquals("p1", state.hostId)
        assertEquals(1, state.players.size)
        assertEquals("Basti", state.players.first().name)
        
        verify(lobbyRepository).save(anyK())
        // Avoid eq() to prevent NPE in Kotlin
        verify(messagingService).sendEventToLobby(anyString(), anyK())
        verify(messagingService).broadcastEvent(anyK())
    }

    @Test
    fun `joinLobby adds a new player and broadcasts`() {
        val created = lobbyService.createLobby("Host", "h1")
        reset(messagingService, lobbyRepository)
        `when`(messagingService.getLobbyLock(anyString())).thenReturn(ReentrantLock())
        
        val updated = lobbyService.joinLobby(created.lobbyCode, "Max", "p2")

        assertEquals(2, updated.players.size)
        verify(messagingService, atLeastOnce()).broadcastEvent(anyK())
        verify(messagingService, atLeastOnce()).sendEventToLobby(anyString(), anyK())
    }

    @Test
    fun `loadFromDb restores state correctly`() {
        val players = listOf(Player("p1", "Alice"))
        val entity = LobbyEntity("1234", "p1", false, objectMapper.writeValueAsString(players), System.currentTimeMillis())
        
        `when`(lobbyRepository.findAll()).thenReturn(listOf(entity))
        
        lobbyService.loadFromDb()
        
        val lobby = lobbyService.getLobby("1234")
        assertEquals("1234", lobby.lobbyCode)
        assertEquals(1, lobby.players.size)
    }
}
