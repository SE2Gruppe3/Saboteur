package com.aau.server.health

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.actuate.health.Status

class GameSystemHealthIndicatorTest {

    private val lobbyService: LobbyService = mock()
    private val messagingService: MessagingService = mock()
    private lateinit var healthIndicator: GameSystemHealthIndicator

    @BeforeEach
    fun setUp() {
        healthIndicator = GameSystemHealthIndicator(lobbyService, messagingService)
    }

    @Test
    fun `health returns UP with correct details`() {
        whenever(lobbyService.getActiveLobbiesCount()).thenReturn(5)
        whenever(messagingService.getActiveSessionsCount()).thenReturn(10)
        whenever(messagingService.getRegisteredPlayersCount()).thenReturn(8)

        val health = healthIndicator.health()

        assertEquals(Status.UP, health.status)
        assertEquals(5, health.details["activeLobbies"])
        assertEquals(10, health.details["connectedWebSockets"])
        assertEquals(8, health.details["registeredPlayersInSessions"])
    }

    @Test
    fun `health returns Slightly Overloaded status when lobbies exceed threshold`() {
        whenever(lobbyService.getActiveLobbiesCount()).thenReturn(501)
        whenever(messagingService.getActiveSessionsCount()).thenReturn(1000)
        whenever(messagingService.getRegisteredPlayersCount()).thenReturn(900)

        val health = healthIndicator.health()

        assertEquals("Slightly Overloaded", health.status.code)
        assertEquals(501, health.details["activeLobbies"])
    }
}
