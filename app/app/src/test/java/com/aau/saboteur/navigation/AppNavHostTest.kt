package com.aau.saboteur.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.ReconnectResponse
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.network.WebSocketManager
import com.aau.saboteur.network.game.GameApi
import com.aau.saboteur.network.game.MapResult
import com.aau.saboteur.network.lobby.LobbyApi
import io.mockk.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class AppNavHostTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: TestNavHostController
    private val lobbyStateUpdates = MutableSharedFlow<LobbyState?>(replay = 1)
    private val reconnectData = MutableSharedFlow<ReconnectResponse>(replay = 1)

    @Before
    fun setup() {
        // Mock the singletons so screens can render without real network/Looper work.
        mockkObject(WebSocketManager)
        every { WebSocketManager.connectionStatus } returns MutableStateFlow(false)
        every { WebSocketManager.connect(any(), any()) } just Runs
        every { WebSocketManager.connect() } just Runs
        every { WebSocketManager.disconnect() } just Runs
        every { WebSocketManager.reset() } just Runs
        every { WebSocketManager.sendCommand(any(), any()) } just Runs
        every { WebSocketManager.onEvent(any(), any()) } just Runs

        mockkObject(LobbyApi)
        every { LobbyApi.lobbyStateUpdates } returns lobbyStateUpdates
        every { LobbyApi.allLobbies } returns MutableSharedFlow<List<LobbyState>>(replay = 1)
        every { LobbyApi.errorMessages } returns MutableSharedFlow<String?>(replay = 1)
        every { LobbyApi.lobbyNotFound } returns MutableSharedFlow<String>(replay = 1)
        every { LobbyApi.reconnectData } returns reconnectData
        every { LobbyApi.fetchAllLobbies() } just Runs
        every { LobbyApi.createLobby(any(), any(), any()) } just Runs
        every { LobbyApi.joinLobby(any(), any(), any()) } just Runs
        every { LobbyApi.leaveLobby(any(), any()) } just Runs
        every { LobbyApi.reconnect(any(), any()) } just Runs

        mockkObject(GameApi)
        every { GameApi.gameStateUpdates } returns MutableStateFlow(GameState())
        every { GameApi.playerUpdates } returns MutableStateFlow<Player?>(null)
        every { GameApi.cardsDealtUpdates } returns MutableStateFlow<Map<String, List<TunnelCard>>?>(null)
        every { GameApi.errorMessages } returns MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 10)
        every { GameApi.gameOverEvents } returns MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
        every { GameApi.validPositionsUpdates } returns MutableSharedFlow<List<BoardPosition>>(replay = 1, extraBufferCapacity = 1)
        every { GameApi.mapResultEvents } returns MutableSharedFlow<MapResult>(replay = 0, extraBufferCapacity = 1)
        every { GameApi.reset() } just Runs
        every { GameApi.startGame(any(), any()) } just Runs

        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            AppNavHost(navController = navController)
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun startDestination_isLogin() {
        composeTestRule.runOnIdle {
            assertEquals("login", navController.currentBackStackEntry?.destination?.route)
        }
    }

    @Test
    fun navigateToLobby_resolvesUsernameArgument() {
        composeTestRule.runOnUiThread { navController.navigate("lobby/lukas") }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertEquals("lobby/{username}", navController.currentBackStackEntry?.destination?.route)
            assertEquals("lukas", navController.currentBackStackEntry?.arguments?.getString("username"))
        }
    }

    @Test
    fun navigateToActiveLobby_resolvesUsernameArgument() {
        // ActiveLobbyScreen auto-pops when lobbyState is null, so seed a non-null state first
        lobbyStateUpdates.tryEmit(mockk<LobbyState>(relaxed = true))
        composeTestRule.runOnUiThread { navController.navigate("activeLobby/sebastian") }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertEquals("activeLobby/{username}", navController.currentBackStackEntry?.destination?.route)
            assertEquals("sebastian", navController.currentBackStackEntry?.arguments?.getString("username"))
        }
    }

    @Test
    fun navigateToGame_succeeds() {
        composeTestRule.runOnUiThread { navController.navigate("game") }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertEquals("game", navController.currentBackStackEntry?.destination?.route)
        }
    }

    @Test
    fun loginToLobby_withPopUpToInclusive_removesLoginFromBackStack() {
        // Mirrors AppNavHost's onAuthClick navigation builder
        composeTestRule.runOnUiThread {
            navController.navigate("lobby/lukas") {
                popUpTo("login") { inclusive = true }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertEquals("lobby/{username}", navController.currentBackStackEntry?.destination?.route)
            assertNull(navController.previousBackStackEntry)
        }
    }

    @Test
    fun activeLobby_autoPopsWhenLobbyStateBecomesNull() {
        // Production behavior: ActiveLobbyScreen's HandleActiveLobbyEffects calls onLeaveLobby()
        // (which is wired to popBackStack) whenever lobbyState transitions to null.
        lobbyStateUpdates.tryEmit(mockk<LobbyState>(relaxed = true))
        composeTestRule.runOnUiThread { navController.navigate("activeLobby/lukas") }
        composeTestRule.waitForIdle()
        assertEquals("activeLobby/{username}", navController.currentBackStackEntry?.destination?.route)

        lobbyStateUpdates.tryEmit(null)
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertNotEquals("activeLobby/{username}", navController.currentBackStackEntry?.destination?.route)
        }
    }

    @Test
    fun gameBackToLobby_withPopUpToZero_clearsBackStack() {
        // Walk the full happy path so backstack is non-trivial before the test
        composeTestRule.runOnUiThread {
            navController.navigate("lobby/lukas")
            navController.navigate("activeLobby/lukas")
            navController.navigate("game")
        }
        composeTestRule.waitForIdle()
        assertEquals("game", navController.currentBackStackEntry?.destination?.route)

        // Mirrors GameScreen's onBackToLobby navigation builder
        composeTestRule.runOnUiThread {
            navController.navigate("lobby/lukas") {
                popUpTo(0) { inclusive = true }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertEquals("lobby/{username}", navController.currentBackStackEntry?.destination?.route)
            assertNull(navController.previousBackStackEntry)
        }
    }

    @Test
    fun lobbyUsernameArg_supportsDifferentValues() {
        composeTestRule.runOnUiThread { navController.navigate("lobby/Chris") }
        composeTestRule.waitForIdle()
        assertEquals("Chris", navController.currentBackStackEntry?.arguments?.getString("username"))

        composeTestRule.runOnUiThread { navController.navigate("lobby/Bastian") }
        composeTestRule.waitForIdle()
        assertEquals("Bastian", navController.currentBackStackEntry?.arguments?.getString("username"))
    }

    @Test
    fun appNavHost_acceptsExplicitModifier() {
        // Re-mount with an explicit modifier so the default-arg bridge's non-default branch fires.
        composeTestRule.activity.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            AppNavHost(navController = navController, modifier = Modifier)
        }
        composeTestRule.waitForIdle()
        assertEquals("login", navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun gameRoute_isDistinctFromLobbyAndActiveLobby() {
        composeTestRule.runOnUiThread { navController.navigate("game") }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            val route = navController.currentBackStackEntry?.destination?.route
            assertNotEquals("lobby/{username}", route)
            assertNotEquals("activeLobby/{username}", route)
            assertEquals("game", route)
        }
    }

    @Test
    fun lobbyScreen_onLobbyJoinedCallback_navigatesToActiveLobby() {
        composeTestRule.runOnUiThread { navController.navigate("lobby/lukas") }
        composeTestRule.waitForIdle()

        val state = mockk<LobbyState>(relaxed = true)
        every { state.lobbyCode } returns "ABC"
        every { state.gameStarted } returns false
        lobbyStateUpdates.tryEmit(state)
        composeTestRule.waitForIdle()

        assertEquals("activeLobby/{username}", navController.currentBackStackEntry?.destination?.route)
        assertEquals("lukas", navController.currentBackStackEntry?.arguments?.getString("username"))
    }

    @Test
    fun lobbyScreen_onGameStartedCallback_navigatesToGame() {
        composeTestRule.runOnUiThread { navController.navigate("lobby/lukas") }
        composeTestRule.waitForIdle()

        val state = mockk<LobbyState>(relaxed = true)
        every { state.lobbyCode } returns "ABC"
        every { state.gameStarted } returns true
        every { state.players } returns emptyList()
        lobbyStateUpdates.tryEmit(state)
        composeTestRule.waitForIdle()

        // Both LobbyScreen LaunchedEffects fire in the same composition pass:
        // onLobbyJoined navigates to activeLobby and onGameStarted navigates to game.
        // The final destination is "game".
        assertEquals("game", navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun activeLobbyScreen_onStartGameCallback_navigatesToGame() {
        // Seed reconnectData so the LobbyViewModel populates BOTH playerId and lobbyState
        val player = mockk<Player>(relaxed = true)
        every { player.id } returns "P1"
        val state = mockk<LobbyState>(relaxed = true)
        every { state.lobbyCode } returns "ABC"
        every { state.gameStarted } returns true
        every { state.players } returns listOf(player)
        reconnectData.tryEmit(ReconnectResponse(myPlayerId = "P1", lobbyState = state))
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread { navController.navigate("activeLobby/lukas") }
        composeTestRule.waitForIdle()

        assertEquals("game", navController.currentBackStackEntry?.destination?.route)
    }
}
