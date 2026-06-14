package com.aau.server.websocket.command

import com.aau.saboteur.model.CheatType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommandImplementationsTest {

    @Test
    fun `PlayerCheatCommand properties and copy work`() {
        val command = PlayerCheatCommand(lobbyCode = "LOBBY1", cheatType = CheatType.LANTERN_FLASHLIGHT)
        
        assertEquals("LOBBY1", command.lobbyCode)
        assertEquals(CheatType.LANTERN_FLASHLIGHT, command.cheatType)
        
        val copied = command.copy(lobbyCode = "LOBBY2")
        assertEquals("LOBBY2", copied.lobbyCode)
        assertEquals(CheatType.LANTERN_FLASHLIGHT, copied.cheatType)
    }

    @Test
    fun `AccuseCheatCommand properties and copy work`() {
        val command = AccuseCheatCommand(lobbyCode = "LOBBY1", accusedPlayerId = "P2")

        assertEquals("LOBBY1", command.lobbyCode)
        assertEquals("P2", command.accusedPlayerId)

        val copied = command.copy(accusedPlayerId = "P3")
        assertEquals("LOBBY1", copied.lobbyCode)
        assertEquals("P3", copied.accusedPlayerId)
    }
}
