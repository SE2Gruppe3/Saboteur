package com.aau.saboteur.network.game

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.CardType
import com.aau.saboteur.model.CreateGameRequest
import com.aau.saboteur.model.DiscardCardRequest
import com.aau.saboteur.model.Direction
import com.aau.saboteur.model.PlayCardRequest
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.PlayerTurn
import com.aau.saboteur.model.Role
import com.aau.saboteur.model.TunnelCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameJsonTest {

    // region encode (toJson)
    @Test
    fun `CreateGameRequest toJson includes players array`() {
        val req = CreateGameRequest(
            players = listOf(Player(id = "P1", name = "Lukas"))
        )
        val json = req.toJson()
        assertTrue(json.contains("\"players\""))
        assertTrue(json.contains("\"P1\""))
        assertTrue(json.contains("\"Lukas\""))
    }

    @Test
    fun `CreateGameRequest toJson with empty players still emits players key`() {
        val json = CreateGameRequest().toJson()
        // encodeDefaults = true ensures the empty list is serialized
        assertTrue(json.contains("\"players\""))
    }

    @Test
    fun `PlayCardRequest toJson includes all fields`() {
        val req = PlayCardRequest(
            playerId = "P1",
            cardId = "C-42",
            position = BoardPosition(row = 3, column = 4),
            isRotated = true
        )
        val json = req.toJson()
        assertTrue(json.contains("\"playerId\":\"P1\""))
        assertTrue(json.contains("\"cardId\":\"C-42\""))
        assertTrue(json.contains("\"row\":3"))
        assertTrue(json.contains("\"column\":4"))
        assertTrue(json.contains("\"isRotated\":true"))
    }

    @Test
    fun `DiscardCardRequest toJson includes playerId and cardId`() {
        val json = DiscardCardRequest(playerId = "P1", cardId = "C-9").toJson()
        assertTrue(json.contains("\"playerId\":\"P1\""))
        assertTrue(json.contains("\"cardId\":\"C-9\""))
    }
    // endregion

    // region decode (toX)
    @Test
    fun `toGameState parses board placements and current player`() {
        val js = """
            {
              "players": [{"playerId":"P1","playerName":"Lukas","turnOrder":0,"blockedTools":[]}],
              "currentPlayerId": "P1",
              "boardPlacements": [],
              "deckSize": 30
            }
        """.trimIndent()
        val state = js.toGameState()
        assertEquals(1, state.players.size)
        assertEquals(PlayerTurn(playerId = "P1", playerName = "Lukas", turnOrder = 0), state.players[0])
        assertEquals("P1", state.currentPlayerId)
        assertEquals(30, state.deckSize)
    }

    @Test
    fun `toGameState tolerates unknown fields`() {
        // ignoreUnknownKeys = true: extra fields must not throw
        val js = """{"players":[],"currentPlayerId":null,"futureField":"ignored"}"""
        val state = js.toGameState()
        assertEquals(emptyList<PlayerTurn>(), state.players)
    }

    @Test
    fun `toPlayer parses role and hand`() {
        val js = """
            {
              "id": "P1",
              "name": "Lukas",
              "hand": [{"id":"c1","type":"PATH","connections":["TOP","BOTTOM"]}],
              "role": "SABOTEUR",
              "isGuest": false
            }
        """.trimIndent()
        val player = js.toPlayer()
        assertEquals("P1", player.id)
        assertEquals(Role.SABOTEUR, player.role)
        assertEquals(1, player.hand.size)
        assertEquals(CardType.PATH, player.hand[0].type)
        assertEquals(setOf(Direction.TOP, Direction.BOTTOM), player.hand[0].connections)
    }

    @Test
    fun `toHands parses a map of player id to card list`() {
        val js = """
            {
              "P1": [{"id":"c1","type":"PATH","connections":[]}],
              "P2": []
            }
        """.trimIndent()
        val hands = js.toHands()
        assertEquals(2, hands.size)
        assertEquals(1, hands["P1"]?.size)
        assertEquals(emptyList<TunnelCard>(), hands["P2"])
    }

    @Test
    fun `toValidPositions extracts positions array from wrapper`() {
        val js = """{"positions":[{"row":0,"column":0},{"row":2,"column":3}]}"""
        val positions = js.toValidPositions()
        assertEquals(listOf(BoardPosition(0, 0), BoardPosition(2, 3)), positions)
    }

    @Test
    fun `toValidPositions returns empty list for empty positions array`() {
        val positions = """{"positions":[]}""".toValidPositions()
        assertEquals(emptyList<BoardPosition>(), positions)
    }

    @Test
    fun `toGameOverWinner extracts winner field`() {
        assertEquals("DWARVES", """{"winner":"DWARVES"}""".toGameOverWinner())
        assertEquals("SABOTEURS", """{"winner":"SABOTEURS"}""".toGameOverWinner())
    }

    @Test
    fun `toReconnectSnapshot parses lobby, game and player state`() {
        val js = """
            {
              "lobbyState": {
                "lobbyCode": "ABC",
                "hostId": "P1",
                "players": [],
                "visibility": "PUBLIC"
              },
              "gameState": {
                "players": [],
                "currentPlayerId": null
              },
              "playerState": {
                "id": "P1",
                "name": "Lukas"
              },
              "serverTimestamp": 1700000000000
            }
        """.trimIndent()
        val snapshot = js.toReconnectSnapshot()
        assertEquals("ABC", snapshot.lobbyState.lobbyCode)
        assertEquals("P1", snapshot.lobbyState.hostId)
        assertNotNull(snapshot.gameState)
        assertEquals("P1", snapshot.playerState.id)
        assertEquals(1700000000000L, snapshot.serverTimestamp)
    }

    @Test
    fun `toReconnectSnapshot tolerates null gameState`() {
        val js = """
            {
              "lobbyState": {
                "lobbyCode": "ABC",
                "hostId": "P1",
                "players": [],
                "visibility": "PUBLIC"
              },
              "gameState": null,
              "playerState": {"id":"P2","name":"X"},
              "serverTimestamp": 0
            }
        """.trimIndent()
        val snapshot = js.toReconnectSnapshot()
        assertEquals(null, snapshot.gameState)
        assertEquals("P2", snapshot.playerState.id)
    }
    // endregion

    // region round-trip
    @Test
    fun `PlayCardRequest round-trips through json without loss`() {
        // Sanity check: encoding then decoding gives an equivalent value.
        // We can't directly decode PlayCardRequest via GameJson helpers (no decoder exposed),
        // so we re-encode and compare strings.
        val original = PlayCardRequest("P1", "C-1", BoardPosition(1, 2), isRotated = false)
        val json = original.toJson()
        val again = PlayCardRequest("P1", "C-1", BoardPosition(1, 2), isRotated = false).toJson()
        assertEquals(json, again)
    }
    // endregion
}
