package com.aau.saboteur.network.game

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aau.saboteur.model.*
import com.aau.saboteur.network.WebSocketManager
import com.aau.saboteur.network.lobby.LobbyApi
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class GameApiTest {

    private val eventHandlers = mutableMapOf<String, (String) -> Unit>()
    private val wsErrors = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 10)
    private val reconnectData = MutableSharedFlow<ReconnectResponse>(replay = 1)

    @Before
    fun setup() {
        mockkObject(WebSocketManager)
        every { WebSocketManager.onEvent(any(), any()) } answers {
            eventHandlers[firstArg()] = secondArg()
        }
        every { WebSocketManager.sendCommand(any(), any()) } just Runs
        every { WebSocketManager.errorMessages } returns wsErrors

        mockkObject(LobbyApi)
        every { LobbyApi.reconnectData } returns reconnectData

        // Re-invoke observation methods to bind to mocked flows/handlers
        val methods = listOf("observeEvents", "observeConnectionErrors", "observeLobbyReconnects")
        methods.forEach { methodName ->
            val method = GameApi::class.java.getDeclaredMethod(methodName)
            method.isAccessible = true
            method.invoke(GameApi)
        }

        GameApi.reset()
    }

    @After
    fun tearDown() {
        // Cancel background coroutines in GameApi to avoid leaks accessing unmocked objects
        runCatching {
            val scopeField = GameApi::class.java.getDeclaredField("scope")
            scopeField.isAccessible = true
            val scope = scopeField.get(GameApi) as CoroutineScope
            scope.coroutineContext.job.cancelChildren()
        }

        unmockkObject(WebSocketManager)
        unmockkObject(LobbyApi)
        eventHandlers.clear()
    }

    private fun fire(event: String, json: String) {
        eventHandlers[event]?.invoke(json)
    }

    // region outbound commands
    @Test
    fun `startGame sends START_GAME with lobbyCode and players`() {
        val players = listOf(Player(id = "P1", name = "Lukas"))
        GameApi.startGame("L1", players)

        verify {
            WebSocketManager.sendCommand("START_GAME", match<JSONObject> { json ->
                json.getString("lobbyCode") == "L1" && json.getJSONArray("players").length() == 1
            })
        }
    }

    @Test
    fun `playCard sends PLAY_CARD with full payload`() {
        GameApi.playCard("L1", "P1", "C1", BoardPosition(3, 4), isRotated = true)
        verify {
            WebSocketManager.sendCommand("PLAY_CARD", match<JSONObject> { json ->
                json.getString("lobbyCode") == "L1" &&
                    json.getString("playerId") == "P1" &&
                    json.getString("cardId") == "C1" &&
                    json.getBoolean("isRotated")
            })
        }
    }

    @Test
    fun `discardCard sends DISCARD_CARD`() {
        GameApi.discardCard("L1", "P1", "C9")
        verify {
            WebSocketManager.sendCommand("DISCARD_CARD", match<JSONObject> { json ->
                json.getString("lobbyCode") == "L1" && json.getString("cardId") == "C9"
            })
        }
    }

    @Test
    fun `requestValidPositions sends GET_VALID_POSITIONS`() {
        GameApi.requestValidPositions("L1", "C2", isRotated = false)
        verify {
            WebSocketManager.sendCommand("GET_VALID_POSITIONS", match<JSONObject> { json ->
                json.getString("lobbyCode") == "L1" &&
                    json.getString("cardId") == "C2" &&
                    !json.getBoolean("isRotated")
            })
        }
    }

    @Test
    fun `playBlockCard sends PLAY_BLOCK_CARD with target`() {
        GameApi.playBlockCard("L1", "P1", "B1", "T1")
        verify {
            WebSocketManager.sendCommand("PLAY_BLOCK_CARD", match<JSONObject> { json ->
                json.getString("targetPlayerId") == "T1" && json.getString("cardId") == "B1"
            })
        }
    }

    @Test
    fun `playRepairCard sends PLAY_REPAIR_CARD with tool`() {
        GameApi.playRepairCard("L1", "P1", "R1", "T1", "PICKAXE")
        verify {
            WebSocketManager.sendCommand("PLAY_REPAIR_CARD", match<JSONObject> { json ->
                json.getString("tool") == "PICKAXE" && json.getString("targetPlayerId") == "T1"
            })
        }
    }

    @Test
    fun `playMapCard sends PLAY_MAP_CARD with targetPosition`() {
        GameApi.playMapCard("L1", "P1", "M1", BoardPosition(2, 3))
        verify {
            WebSocketManager.sendCommand("PLAY_MAP_CARD", match<JSONObject> { json ->
                val pos = json.getJSONObject("targetPosition")
                pos.getInt("row") == 2 && pos.getInt("column") == 3
            })
        }
    }

    @Test
    fun `playRockfallCard sends PLAY_ROCKFALL_CARD with targetPosition`() {
        GameApi.playRockfallCard("L1", "P1", "RF1", BoardPosition(0, 0))
        verify {
            WebSocketManager.sendCommand("PLAY_ROCKFALL_CARD", match<JSONObject> { json ->
                val pos = json.getJSONObject("targetPosition")
                pos.getInt("row") == 0 && pos.getInt("column") == 0
            })
        }
    }
    // endregion

    // region inbound event handlers
    @Test
    fun `GAME_STATE_UPDATE event updates gameStateUpdates flow`() {
        fire("GAME_STATE_UPDATE", """{"players":[],"currentPlayerId":"P1","deckSize":42}""")
        assertEquals("P1", GameApi.gameStateUpdates.value.currentPlayerId)
        assertEquals(42, GameApi.gameStateUpdates.value.deckSize)
    }

    @Test
    fun `GAME_STATE_UPDATE swallows malformed json`() {
        fire("GAME_STATE_UPDATE", "not valid json")
        assertEquals(GameState(), GameApi.gameStateUpdates.value)
    }

    @Test
    fun `PLAYER_DATA event updates playerUpdates`() {
        fire("PLAYER_DATA", """{"id":"P1","name":"Lukas","hand":[],"role":"GOLDDIGGER"}""")
        assertEquals("P1", GameApi.playerUpdates.value?.id)
        assertEquals("Lukas", GameApi.playerUpdates.value?.name)
    }

    @Test
    fun `PLAYER_DATA with non-empty hand also seeds cardsDealtUpdates`() {
        fire(
            "PLAYER_DATA",
            """{"id":"P1","name":"X","hand":[{"id":"c1","type":"PATH","connections":[]}]}"""
        )
        val hands = GameApi.cardsDealtUpdates.value
        assertNotNull(hands)
        assertEquals(1, hands?.get("P1")?.size)
    }

    @Test
    fun `CARDS_DEALT event updates cardsDealtUpdates`() {
        fire("CARDS_DEALT", """{"P1":[{"id":"c1","type":"PATH","connections":[]}],"P2":[]}""")
        assertEquals(2, GameApi.cardsDealtUpdates.value?.size)
        assertEquals(0, GameApi.cardsDealtUpdates.value?.get("P2")?.size)
    }

    @Test
    fun `GAME_OVER event emits winner`() = runTest {
        val collector = async(UnconfinedTestDispatcher(testScheduler)) { GameApi.gameOverEvents.first() }
        fire("GAME_OVER", """{"winner":"GOLDDIGGERS"}""")
        assertEquals("GOLDDIGGERS", collector.await())
    }

    @Test
    fun `VALID_POSITIONS event updates the validPositions flow`() = runTest {
        fire("VALID_POSITIONS", """{"positions":[{"row":1,"column":2}]}""")
        val emitted = GameApi.validPositionsUpdates.first()
        assertEquals(listOf(BoardPosition(1, 2)), emitted)
    }

    @Test
    fun `RECONNECT_SNAPSHOT event updates state and sends SYNC_ACK`() {
        val snapshot = """
            {
              "lobbyState": {"lobbyCode":"ABC","hostId":"P1","players":[],"visibility":"PUBLIC"},
              "gameState": {"players":[],"currentPlayerId":"P1","deckSize":5},
              "playerState": {"id":"P1","name":"Lukas","hand":[{"id":"c1","type":"PATH","connections":[]}]},
              "serverTimestamp": 1000
            }
        """.trimIndent()
        fire("RECONNECT_SNAPSHOT", snapshot)

        assertEquals("P1", GameApi.gameStateUpdates.value.currentPlayerId)
        assertEquals(5, GameApi.gameStateUpdates.value.deckSize)
        assertEquals("P1", GameApi.playerUpdates.value?.id)
        assertEquals(1, GameApi.cardsDealtUpdates.value?.get("P1")?.size)
        verify { WebSocketManager.sendCommand("SYNC_ACK", any()) }
    }

    @Test
    fun `MAP_RESULT event emits parsed result`() = runTest {
        val resultDeferred = async(UnconfinedTestDispatcher(testScheduler)) { GameApi.mapResultEvents.first() }
        fire(
            "MAP_RESULT",
            """{"position":{"row":3,"column":4},"card":{"id":"g","type":"GOAL","connections":[],"isGoal":true}}"""
        )
        val result = resultDeferred.await()
        assertEquals(BoardPosition(3, 4), result.position)
        assertEquals(CardType.GOAL, result.card.type)
        assertTrue(result.card.isGoal)
    }

    @Test
    fun `ERROR event emits raw payload`() = runTest {
        val msgDeferred = async(UnconfinedTestDispatcher(testScheduler)) { GameApi.errorMessages.first() }
        fire("ERROR", "error.invalid_placement")
        val msg = msgDeferred.await()
        assertEquals("error.invalid_placement", msg)
    }

    @Test
    fun `connection errors from WebSocketManager are forwarded to errorMessages`() = runTest {
        // Since GameApi collects on IO dispatcher, we need to wait a bit for it to start
        // or ensure it's bound. Re-binding in setup should have launched it.
        val msgDeferred = async { GameApi.errorMessages.first() }
        
        // Wait for the collector to be ready
        var attempts = 0
        while (attempts < 5) {
            wsErrors.emit("Connection lost")
            val result = withTimeoutOrNull(200) { msgDeferred.await() }
            if (result == "Connection lost") break
            attempts++
        }
        assertEquals("Connection lost", msgDeferred.await())
    }

    @Test
    fun `LobbyApi reconnectData updates GameApi state`() = runTest {
        val mockLobbyState = LobbyState(
            lobbyCode = "L1",
            hostId = "P1",
            players = listOf(Player(id = "P1", name = "Lukas")),
            visibility = LobbyVisibility.PUBLIC
        )
        val mockReconnectResponse = ReconnectResponse(
            myPlayerId = "P1",
            lobbyState = mockLobbyState,
            gameState = GameState(currentPlayerId = "P1", deckSize = 10),
            playerRole = Role.GOLDDIGGER,
            playerHand = listOf(TunnelCard(id = "c1", type = CardType.PATH, connections = emptySet()))
        )

        reconnectData.emit(mockReconnectResponse)

        // The flow collection happens on IO, so we may need a small wait
        var lastState: GameState? = null
        withTimeout(2000) {
            while (lastState?.currentPlayerId != "P1") {
                lastState = GameApi.gameStateUpdates.value
                delay(10)
            }
        }

        assertEquals("P1", GameApi.gameStateUpdates.value.currentPlayerId)
        assertEquals(10, GameApi.gameStateUpdates.value.deckSize)
    }
    // endregion

    // region lifecycle
    @Test
    fun `reset clears gameStateUpdates and player and hands`() {
        fire("GAME_STATE_UPDATE", """{"players":[],"currentPlayerId":"P1","deckSize":10}""")
        fire("PLAYER_DATA", """{"id":"P1","name":"L"}""")
        fire("CARDS_DEALT", """{"P1":[]}""")
        assertEquals("P1", GameApi.gameStateUpdates.value.currentPlayerId)

        GameApi.reset()

        assertEquals(GameState(), GameApi.gameStateUpdates.value)
        assertNull(GameApi.playerUpdates.value)
        assertNull(GameApi.cardsDealtUpdates.value)
    }

    @Test
    fun `clearValidPositions emits empty list`() = runTest {
        val positionsDeferred = async { GameApi.validPositionsUpdates.first() }
        GameApi.clearValidPositions()
        val positions = positionsDeferred.await()
        assertEquals(emptyList<BoardPosition>(), positions)
    }
    // endregion
}
