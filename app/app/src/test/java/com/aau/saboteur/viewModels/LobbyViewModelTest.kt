package com.aau.saboteur.viewModels

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aau.saboteur.R
import com.aau.saboteur.data.repository.SessionRepository
import com.aau.saboteur.model.*
import com.aau.saboteur.network.WebSocketManager
import com.aau.saboteur.network.game.GameApi
import com.aau.saboteur.network.lobby.LobbyApi
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class LobbyViewModelTest {

    private val application = mockk<Application>(relaxed = true)
    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    
    // UnconfinedTestDispatcher is critical for testing init blocks and StateFlows
    // as it executes launched tasks immediately on the current thread.
    private val testDispatcher = UnconfinedTestDispatcher()
    
    private val lobbyStateUpdates = MutableSharedFlow<LobbyState?>(replay = 1)
    private val allLobbies = MutableSharedFlow<List<LobbyState>>(replay = 1)
    private val errorMessages = MutableSharedFlow<String?>(replay = 1)
    private val lobbyNotFound = MutableSharedFlow<String>(replay = 1)
    private val reconnectData = MutableSharedFlow<ReconnectResponse>(replay = 1)
    private val playerKicked = MutableSharedFlow<String>(replay = 1)
    private val connectionStatus = MutableStateFlow(false)

    private val eventHandlers = mutableMapOf<String, (String) -> Unit>()

    private lateinit var viewModel: LobbyViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // 1. Mock WebSocketManager (Object)
        mockkObject(WebSocketManager)
        every { WebSocketManager.connectionStatus } returns connectionStatus
        every { WebSocketManager.connect(any(), any()) } just Runs
        every { WebSocketManager.connect() } just Runs
        every { WebSocketManager.disconnect() } just Runs
        every { WebSocketManager.reset() } just Runs
        every { WebSocketManager.sendCommand(any(), any()) } just Runs
        every { WebSocketManager.onEvent(any(), any()) } answers {
            eventHandlers[firstArg()] = secondArg()
        }

        // 2. Mock LobbyApi (Object)
        mockkObject(LobbyApi)
        every { LobbyApi.lobbyStateUpdates } returns lobbyStateUpdates
        every { LobbyApi.allLobbies } returns allLobbies
        every { LobbyApi.errorMessages } returns errorMessages
        every { LobbyApi.lobbyNotFound } returns lobbyNotFound
        every { LobbyApi.reconnectData } returns reconnectData
        every { LobbyApi.playerKicked } returns playerKicked
        every { LobbyApi.fetchAllLobbies() } just Runs
        every { LobbyApi.createLobby(any(), any(), any()) } just Runs
        every { LobbyApi.joinLobby(any(), any(), any()) } just Runs
        every { LobbyApi.leaveLobby(any(), any()) } just Runs
        every { LobbyApi.reconnect(any(), any()) } just Runs
        every { LobbyApi.kickPlayer(any(), any(), any()) } just Runs

        // 3. Mock GameApi (Object)
        mockkObject(GameApi)
        every { GameApi.reset() } just Runs
        every { GameApi.startGame(any(), any()) } just Runs

        // 4. Default SessionRepository mock
        every { sessionRepository.getPlayerId() } returns null
        every { sessionRepository.getLobbyCode() } returns null
        every { sessionRepository.getUserName() } returns "TestGuest"

        viewModel = LobbyViewModel(application, sessionRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `setUsername should update username state`() {
        val newName = "SaboteurPlayer"
        viewModel.setUsername(newName)
        assertEquals(newName, viewModel.username.value)
    }

    @Test
    fun `saveIdentity should update state and clear lobby if PID changes`() {
        val pid = "PID123"
        val name = "Tester"
        viewModel.saveIdentity(pid, name)

        assertEquals(pid, viewModel.playerId.value)
        assertEquals(name, viewModel.username.value)
        verify { sessionRepository.saveIdentity(pid, name) }
    }

    @Test
    fun `createLobby should clear current lobby and notify API`() {
        viewModel.createLobby("Creator", LobbyVisibility.PRIVATE)

        assertEquals(true, viewModel.isSyncing.value)
        verify { sessionRepository.clearLobby() }
        verify { WebSocketManager.disconnect() }
        verify { LobbyApi.createLobby("Creator", null, LobbyVisibility.PRIVATE) }
    }

    @Test
    fun `lobbyState updates should stop syncing and reflect state`() = runTest {
        val mockState = mockk<LobbyState>(relaxed = true)
        every { mockState.lobbyCode } returns "ABCD"

        lobbyStateUpdates.emit(mockState)

        assertEquals(mockState, viewModel.lobbyState.value)
        assertEquals(false, viewModel.isSyncing.value)
    }

    @Test
    fun `errorMessages with 403 or 404 should clear session`() = runTest {
        // Trigger a state where we are in a lobby
        val mockState = mockk<LobbyState>(relaxed = true)
        lobbyStateUpdates.emit(mockState)

        errorMessages.emit("Error 403: Forbidden")

        assertEquals(null, viewModel.lobbyState.value)
        verify { sessionRepository.clearLobby() }
        verify { WebSocketManager.reset() }
    }

    @Test
    fun `SYNC_COMPLETE event should reset syncing state`() {
        // Force syncing to true manually via an action
        viewModel.joinLobby("CODE", "Name")
        assertEquals(true, viewModel.isSyncing.value)

        // Trigger SYNC_COMPLETE callback captured in setup
        eventHandlers["SYNC_COMPLETE"]?.invoke("")
        
        assertEquals(false, viewModel.isSyncing.value)
    }

    @Test
    fun `connection established should refresh lobby list`() = runTest {
        connectionStatus.value = true
        verify { LobbyApi.fetchAllLobbies() }
    }

    @Test
    fun `leaveLobby should notify API and clear local state`() = runTest {
        val mockState = mockk<LobbyState>(relaxed = true)
        every { mockState.lobbyCode } returns "LOBBY_CODE"
        viewModel.saveIdentity("P1", "Name")
        lobbyStateUpdates.emit(mockState)
        
        viewModel.leaveLobby()
        
        verify { LobbyApi.leaveLobby("LOBBY_CODE", "P1") }
        assertEquals(null, viewModel.lobbyState.value)
        verify { sessionRepository.clearLobby() }
        verify { WebSocketManager.disconnect() }
    }

    @Test
    fun `startGame should delegate to GameApi when players present`() = runTest {
        val players = listOf(mockk<Player>())
        val mockState = mockk<LobbyState>(relaxed = true)
        every { mockState.lobbyCode } returns "G1"
        every { mockState.players } returns players
        lobbyStateUpdates.emit(mockState)
        
        viewModel.startGame()

        verify { GameApi.startGame("G1", players) }
    }

    @Test
    fun `initialization should attempt auto-reconnect if session exists`() {
        val mockRepo = mockk<SessionRepository>(relaxed = true)
        every { mockRepo.getPlayerId() } returns "PID"
        every { mockRepo.getLobbyCode() } returns "CODE"
        
        val vm = LobbyViewModel(application, mockRepo)
        
        verify { LobbyApi.reconnect("PID", "CODE") }
        assertEquals(true, vm.isSyncing.value)
    }

    @Test
    fun `reconnect failure during initial attempt should not set error message`() = runTest {
        val mockRepo = mockk<SessionRepository>(relaxed = true)
        every { mockRepo.getPlayerId() } returns "PID"
        every { mockRepo.getLobbyCode() } returns "CODE"

        val vm = LobbyViewModel(application, mockRepo)

        // Initial auto reconnect is active, emit error
        errorMessages.emit("Reconnect failed")

        assertEquals(false, vm.isSyncing.value)
        assertEquals(null, vm.errorMessage.value)
    }

    @Test
    fun `allLobbies updates should populate availableLobbies`() = runTest {
        val lobbies = listOf(mockk<LobbyState>(relaxed = true), mockk<LobbyState>(relaxed = true))
        allLobbies.emit(lobbies)
        assertEquals(lobbies, viewModel.availableLobbies.value)
    }

    @Test
    fun `null lobbyState update should clear session lobby`() = runTest {
        lobbyStateUpdates.emit(null)
        assertEquals(null, viewModel.lobbyState.value)
        verify { sessionRepository.clearLobby() }
    }

    @Test
    fun `reconnectData should update identity and save session`() = runTest {
        val mockState = mockk<LobbyState>(relaxed = true)
        every { mockState.lobbyCode } returns "RECON_CODE"
        val response = ReconnectResponse(myPlayerId = "RECON_PID", lobbyState = mockState)

        reconnectData.emit(response)

        assertEquals("RECON_PID", viewModel.playerId.value)
        assertEquals(mockState, viewModel.lobbyState.value)
        verify { sessionRepository.saveSession("RECON_PID", "RECON_CODE", any()) }
    }

    @Test
    fun `lobbyNotFound should clear state and reset connection`() = runTest {
        val mockState = mockk<LobbyState>(relaxed = true)
        lobbyStateUpdates.emit(mockState)

        lobbyNotFound.emit("Lobby gone")

        assertEquals("Lobby gone", viewModel.errorMessage.value)
        assertEquals(null, viewModel.lobbyState.value)
        verify { sessionRepository.clearLobby() }
        verify { WebSocketManager.reset() }
    }

    @Test
    fun `lobbyNotFound during initial auto-reconnect should clear silently`() = runTest {
        val mockRepo = mockk<SessionRepository>(relaxed = true)
        every { mockRepo.getPlayerId() } returns "PID"
        every { mockRepo.getLobbyCode() } returns "CODE"

        val vm = LobbyViewModel(application, mockRepo)

        lobbyNotFound.emit("Lobby gone")

        assertEquals(null, vm.errorMessage.value)
        assertEquals(null, vm.lobbyState.value)
        assertEquals(false, vm.isSyncing.value)
    }

    @Test
    fun `errorMessages with 404 should clear session`() = runTest {
        val mockState = mockk<LobbyState>(relaxed = true)
        lobbyStateUpdates.emit(mockState)

        errorMessages.emit("Error 404: Not Found")

        assertEquals(null, viewModel.lobbyState.value)
        verify { WebSocketManager.reset() }
    }

    @Test
    fun `generic errorMessage should set message without clearing lobby`() = runTest {
        val mockState = mockk<LobbyState>(relaxed = true)
        every { mockState.lobbyCode } returns "STAY"
        lobbyStateUpdates.emit(mockState)

        errorMessages.emit("Some other server error")

        assertEquals("Some other server error", viewModel.errorMessage.value)
        assertEquals(mockState, viewModel.lobbyState.value)
    }

    @Test
    fun `resetLobby should clear state and reset network`() {
        viewModel.resetLobby()

        assertEquals(null, viewModel.lobbyState.value)
        verify { sessionRepository.clearLobby() }
        verify { WebSocketManager.reset() }
        verify { GameApi.reset() }
    }

    @Test
    fun `leaveLobby without lobby should be a no-op`() {
        viewModel.leaveLobby()
        verify(exactly = 0) { LobbyApi.leaveLobby(any(), any()) }
    }

    @Test
    fun `leaveLobby without playerId should be a no-op`() = runTest {
        val mockState = mockk<LobbyState>(relaxed = true)
        lobbyStateUpdates.emit(mockState)
        // playerId is still null because sessionRepository.getPlayerId() returns null

        viewModel.leaveLobby()

        verify(exactly = 0) { LobbyApi.leaveLobby(any(), any()) }
    }

    @Test
    fun `startGame without lobby should be a no-op`() {
        viewModel.startGame()
        verify(exactly = 0) { GameApi.startGame(any(), any()) }
    }

    @Test
    fun `startGame with empty players should be a no-op`() = runTest {
        val mockState = mockk<LobbyState>(relaxed = true)
        every { mockState.lobbyCode } returns "EMPTY"
        every { mockState.players } returns emptyList()
        lobbyStateUpdates.emit(mockState)

        viewModel.startGame()

        verify(exactly = 0) { GameApi.startGame(any(), any()) }
    }

    @Test
    fun `saveIdentity with unchanged pid should not clear lobby a second time`() {
        viewModel.saveIdentity("SAME_PID", "First")
        viewModel.saveIdentity("SAME_PID", "Second")

        // clearLobby fires once on the first call (initial pid was null), not on the second
        verify(exactly = 1) { sessionRepository.clearLobby() }
        assertEquals("Second", viewModel.username.value)
    }

    @Test
    fun `createLobby should default to PUBLIC visibility`() {
        viewModel.createLobby("DefaultUser")
        verify { LobbyApi.createLobby("DefaultUser", null, LobbyVisibility.PUBLIC) }
    }

    @Test
    fun `attemptAutoReconnect with playerId but no lobbyCode should not reconnect`() {
        val mockRepo = mockk<SessionRepository>(relaxed = true)
        every { mockRepo.getPlayerId() } returns "PID_ONLY"
        every { mockRepo.getLobbyCode() } returns null

        LobbyViewModel(application, mockRepo)

        verify(exactly = 0) { LobbyApi.reconnect(any(), any()) }
    }

    @Test
    fun `kickPlayer should call LobbyApi if caller is host`() = runTest {
        val mockState = mockk<LobbyState>(relaxed = true)
        every { mockState.lobbyCode } returns "L1"
        every { mockState.hostId } returns "HOST_ID"

        viewModel.saveIdentity("HOST_ID", "Host")
        lobbyStateUpdates.emit(mockState)

        viewModel.kickPlayer("TARGET_ID")

        verify { LobbyApi.kickPlayer("L1", "HOST_ID", "TARGET_ID") }
    }

    @Test
    fun `kickPlayer should NOT call LobbyApi if caller is NOT host`() = runTest {
        val mockState = mockk<LobbyState>(relaxed = true)
        every { mockState.lobbyCode } returns "L1"
        every { mockState.hostId } returns "OTHER_HOST"

        viewModel.saveIdentity("ME", "NotHost")
        lobbyStateUpdates.emit(mockState)

        viewModel.kickPlayer("TARGET_ID")

        verify(exactly = 0) { LobbyApi.kickPlayer(any(), any(), any()) }
    }

    @Test
    fun `kickPlayer should be a no-op if no lobby state`() {
        viewModel.kickPlayer("T")
        verify(exactly = 0) { LobbyApi.kickPlayer(any(), any(), any()) }
    }

    @Test
    fun `kickPlayer should be a no-op if no playerId`() = runTest {
        val mockState = mockk<LobbyState>(relaxed = true)
        lobbyStateUpdates.emit(mockState)
        // Initial state has playerId as null

        viewModel.kickPlayer("T")
        verify(exactly = 0) { LobbyApi.kickPlayer(any(), any(), any()) }
    }

    @Test
    fun `playerKicked event for local player should reset everything in order`() = runTest {
        viewModel.saveIdentity("ME", "MyName")
        val kickedMsg = "Kicked from lobby"
        every { application.getString(R.string.player_kicked_msg) } returns kickedMsg

        playerKicked.emit("ME")

        assertEquals(kickedMsg, viewModel.errorMessage.value)
        assertNull(viewModel.lobbyState.value)

        io.mockk.verifyOrder {
            sessionRepository.clearLobby()
            WebSocketManager.disconnect()
            WebSocketManager.reset()
            WebSocketManager.connect()
        }
    }
}
