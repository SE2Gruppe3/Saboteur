package com.aau.server

import com.aau.shared.game.CreateGameRequest
import com.aau.shared.game.GameState
import com.aau.shared.game.Player
import com.aau.shared.game.PlayerTurn
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SharedModelTests {

    @Test
    fun `Player data class methods`() {
        val p1 = Player("1", "A")
        val p2 = Player("1", "A")
        val p3 = Player("2", "B")
        
        assertEquals(p1, p2)
        assertNotEquals(p1, p3)
        assertEquals(p1.hashCode(), p2.hashCode())
        assertEquals("Player(id=1, name=A)", p1.toString())
        
        val p4 = p1.copy(id = "3")
        assertEquals("3", p4.id)
        assertEquals("A", p4.name)
        
        assertEquals("1", p1.component1())
        assertEquals("A", p1.component2())
        
        // Test default constructor branches
        val default = Player()
        assertEquals("", default.id)
        assertEquals("", default.name)
    }

    @Test
    fun `PlayerTurn data class methods`() {
        val pt1 = PlayerTurn("1", "A", 1)
        val pt2 = PlayerTurn("1", "A", 1)
        
        assertEquals(pt1, pt2)
        assertEquals(pt1.hashCode(), pt2.hashCode())
        assertEquals("PlayerTurn(playerId=1, playerName=A, turnOrder=1)", pt1.toString())
        
        val pt3 = pt1.copy(turnOrder = 2)
        assertEquals(2, pt3.turnOrder)
        
        assertEquals("1", pt1.component1())
        assertEquals("A", pt1.component2())
        assertEquals(1, pt1.component3())

        val default = PlayerTurn()
        assertEquals("", default.playerId)
        assertEquals(0, default.turnOrder)
    }

    @Test
    fun `GameState data class methods`() {
        val gs1 = GameState(emptyList(), "1")
        val gs2 = GameState(emptyList(), "1")
        
        assertEquals(gs1, gs2)
        assertEquals(gs1.hashCode(), gs2.hashCode())
        assertEquals("GameState(players=[], currentPlayerId=1)", gs1.toString())
        
        val gs3 = gs1.copy(currentPlayerId = null)
        assertNull(gs3.currentPlayerId)
        
        assertEquals(emptyList<PlayerTurn>(), gs1.component1())
        assertEquals("1", gs1.component2())

        val default = GameState()
        assertTrue(default.players.isEmpty())
        assertNull(default.currentPlayerId)
    }

    @Test
    fun `CreateGameRequest data class methods`() {
        val req1 = CreateGameRequest(emptyList())
        val req2 = CreateGameRequest(emptyList())
        
        assertEquals(req1, req2)
        assertEquals(req1.hashCode(), req2.hashCode())
        assertEquals("CreateGameRequest(players=[])", req1.toString())
        
        val req3 = req1.copy(players = listOf(Player("1", "A")))
        assertEquals(1, req3.players.size)
        
        assertEquals(emptyList<Player>(), req1.component1())

        val default = CreateGameRequest()
        assertTrue(default.players.isEmpty())
    }
}
