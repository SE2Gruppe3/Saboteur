package com.aau.server

import com.aau.server.repository.GameRepository
import com.aau.server.repository.LobbyRepository
import com.aau.server.repository.UserRepository
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.service.GameService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.concurrent.locks.ReentrantLock

class LobbyCleanupTests {

    private lateinit var lobbyService: LobbyService
    private lateinit var lobbyRepository: LobbyRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var gameService: GameService
    private lateinit var messagingService: MessagingService
    private lateinit var turnManager: TurnManager
    private lateinit var userRepository: UserRepository
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setup() {
        lobbyRepository = mock(LobbyRepository::class.java)
        gameRepository = mock(GameRepository::class.java)
        gameService = mock(GameService::class.java)
        messagingService = mock(MessagingService::class.java)
        turnManager = mock(TurnManager::class.java)
        userRepository = mock(UserRepository::class.java)

        `when`(messagingService.getLobbyLock(anyString())).thenReturn(ReentrantLock())
        `when`(userRepository.findByPlayerId(anyString())).thenReturn(null)

        lobbyService = LobbyService(
            lobbyRepository,
            gameRepository,
            objectMapper,
            gameService,
            messagingService,
            turnManager,
            userRepository
        )
    }

    @Test
    fun `cleanup removes empty lobby immediately after last player leaves`() {
        // Implementation of leaveLobby calls deleteLobbyInternal if empty
        val lobby = lobbyService.createLobby("Host", "p1")
        assertNotNull(lobbyService.getLobby(lobby.lobbyCode))

        // Last player leaves triggers immediate cleanup
        lobbyService.leaveLobby(lobby.lobbyCode, "p1")

        assertThrows(NoSuchElementException::class.java) {
            lobbyService.getLobby(lobby.lobbyCode)
        }
        verify(lobbyRepository).deleteById(lobby.lobbyCode)
        verify(messagingService).clearLobbyMappings(lobby.lobbyCode)
        verify(turnManager).removeGame(lobby.lobbyCode)
    }
}
