package com.aau.server

import com.aau.saboteur.model.*
import com.aau.server.service.*
import com.aau.server.websocket.command.RegisterCommand
import com.aau.server.websocket.command.SyncAckCommand
import com.aau.server.websocket.command.handlers.RegisterHandler
import com.aau.server.websocket.command.handlers.SyncAckHandler
import com.aau.server.websocket.event.GameEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

class ReconnectFlowTests {

    private lateinit var messagingService: MessagingService
    private lateinit var lobbyService: LobbyService
    private lateinit var gameService: GameService
    private lateinit var turnManager: TurnManager
    private lateinit var registerHandler: RegisterHandler
    private lateinit var syncAckHandler: SyncAckHandler
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setup() {
        messagingService = MessagingService(objectMapper)
        lobbyService = mock(LobbyService::class.java)
        gameService = mock(GameService::class.java)
        turnManager = mock(TurnManager::class.java)
        
        registerHandler = RegisterHandler(messagingService, lobbyService, gameService, turnManager)
        syncAckHandler = SyncAckHandler(messagingService)
    }

    @Test
    fun `REGISTER generates full snapshot and buffers events until SYNC_ACK`() {
        val sessionId = "session-1"
        val playerId = "player-1"
        val lobbyCode = "LOBBY1"
        val session = mock(WebSocketSession::class.java)
        `when`(session.id).thenReturn(sessionId)
        `when`(session.isOpen).thenReturn(true)

        val lobby = LobbyState(lobbyCode, playerId, listOf(Player(playerId, "Name")), gameStarted = true)
        `when`(lobbyService.getLobby(lobbyCode)).thenReturn(lobby)
        `when`(turnManager.getGameState(lobbyCode)).thenReturn(GameState())
        `when`(gameService.getPlayer(lobbyCode, playerId)).thenReturn(Player(playerId, "Name"))

        messagingService.addSession(session)

        // 1. REGISTER
        registerHandler.handle(session, RegisterCommand(playerId, lobbyCode))

        // Verify Snapshot was sent (MessagingService sends system events directly)
        verify(session, atLeastOnce()).sendMessage(argThat { msg -> 
            (msg as TextMessage).payload.contains("RECONNECT_SNAPSHOT") 
        })

        // 2. Broadcast event for lobby (should be buffered because SYNCING)
        messagingService.sendEventToLobby(lobbyCode, GameEvent.GameOver("DWARVES"))
        
        // At this point, only Snapshot (and maybe Error/SyncComplete) was sent. 
        // GameOver should NOT have been sent yet.
        verify(session, never()).sendMessage(argThat { msg -> 
            (msg as TextMessage).payload.contains("GAME_OVER") 
        })

        // 3. Send SYNC_ACK
        syncAckHandler.handle(session, SyncAckCommand())

        // Buffer should be flushed and SYNC_COMPLETE should be sent
        verify(session).sendMessage(argThat { msg -> 
            (msg as TextMessage).payload.contains("GAME_OVER") 
        })
        verify(session).sendMessage(argThat { msg -> 
            (msg as TextMessage).payload.contains("SYNC_COMPLETE") 
        })
    }

    @Test
    fun `REGISTER force replaces old session for same player`() {
        val oldSid = "old-session"
        val newSid = "new-session"
        val playerId = "p1"
        
        val oldSession = mock(WebSocketSession::class.java)
        val newSession = mock(WebSocketSession::class.java)
        `when`(oldSession.id).thenReturn(oldSid)
        `when`(oldSession.isOpen).thenReturn(true)
        `when`(newSession.id).thenReturn(newSid)
        `when`(newSession.isOpen).thenReturn(true)
        
        messagingService.addSession(oldSession)
        messagingService.registerPlayer(oldSid, playerId)
        
        assertEquals(oldSid, messagingService.getActiveSessionForPlayer(playerId))
        
        messagingService.addSession(newSession)
        messagingService.registerPlayer(newSid, playerId)
        
        // Old session mapping should be replaced in registry
        assertEquals(newSid, messagingService.getActiveSessionForPlayer(playerId))
        
        // Mark new session as SYNCED so messages are not buffered
        messagingService.setSessionSynced(newSid)
        
        // MessagingService should only send to the new session
        messagingService.sendEventToPlayer(playerId, GameEvent.ErrorEvent("test"))
        
        // Expecting 2 messages: SYNC_COMPLETE from setSessionSynced and ErrorEvent from sendEventToPlayer
        verify(newSession, times(2)).sendMessage(any(TextMessage::class.java))
        verify(oldSession, never()).sendMessage(any(TextMessage::class.java))
    }
}
