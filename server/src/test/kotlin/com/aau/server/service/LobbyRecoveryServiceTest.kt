package com.aau.server.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class LobbyRecoveryServiceTest {

    private val lobbyService: LobbyService = mock()
    private val turnManager: TurnManager = mock()
    private lateinit var lobbyRecoveryService: LobbyRecoveryService

    @BeforeEach
    fun setUp() {
        lobbyRecoveryService = LobbyRecoveryService(lobbyService, turnManager)
    }

    @Test
    fun `recoverState calls loadFromDb on both services`() {
        whenever(lobbyService.loadFromDb()).doReturn(5)
        whenever(turnManager.loadFromDb()).doReturn(3)

        lobbyRecoveryService.recoverState()

        verify(lobbyService).loadFromDb()
        verify(turnManager).loadFromDb()
    }
}
