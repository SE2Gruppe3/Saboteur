package com.aau.server

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameMockDataTests {

    @Test
    fun `mockPlayerTurns is not empty`() {
        assertFalse(mockPlayerTurns.isEmpty())
        assertEquals(4, mockPlayerTurns.size)
    }

    @Test
    fun `mockGameState is correctly initialized`() {
        assertNotNull(mockGameState)
        assertEquals(4, mockGameState.players.size)
        assertEquals("1", mockGameState.currentPlayerId)
    }
}
