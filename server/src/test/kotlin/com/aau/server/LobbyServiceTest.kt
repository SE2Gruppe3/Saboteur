package com.aau.server

import com.aau.server.service.LobbyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LobbyServiceTest {

    private val lobbyService = LobbyService()

    @Test
    fun `createLobby returns lobby with host as first player`() {
        val state = lobbyService.createLobby("Basti")

        assertTrue(state.lobbyCode.isNotBlank())

        assertEquals("Basti", state.players.first { it.id == state.hostId }.name)

        assertEquals(1, state.players.size)
        val host = state.players.first()
        assertEquals("Basti", host.name)
        // Host ist korrekt über hostId identifizierbar
        assertEquals(host.id, state.hostId)
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
    }

    @Test
    fun `joinLobby unknown code throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            lobbyService.joinLobby("9999", "Max")
        }
    }
}