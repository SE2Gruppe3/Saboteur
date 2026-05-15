package com.aau.server

import com.aau.saboteur.model.WsMessage
import com.aau.server.service.MessagingService
import com.aau.server.websocket.event.GameEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.CloseStatus

class MessagingServiceTests {

    private lateinit var messagingService: MessagingService
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setup() {
        messagingService = MessagingService(objectMapper)
    }

    @Test
    fun `broadcastEvent sends message to all open synced sessions`() {
        val session1 = mock<WebSocketSession>()
        val session2 = mock<WebSocketSession>()
        whenever(session1.isOpen).thenReturn(true)
        whenever(session2.isOpen).thenReturn(true)
        whenever(session1.id).thenReturn("s1")
        whenever(session2.id).thenReturn("s2")

        messagingService.addSession(session1)
        messagingService.addSession(session2)
        
        messagingService.registerPlayer("s1", "p1")
        messagingService.registerPlayer("s2", "p2")
        messagingService.setSessionSynced("s1")
        messagingService.setSessionSynced("s2")

        val event = GameEvent.ErrorEvent("test-error")
        messagingService.broadcastEvent(event)

        val expectedPayload = objectMapper.writeValueAsString(WsMessage(event.type, event.payload))
        
        verify(session1, atLeastOnce()).sendMessage(argThat { msg -> (msg as TextMessage).payload == expectedPayload })
        verify(session2, atLeastOnce()).sendMessage(argThat { msg -> (msg as TextMessage).payload == expectedPayload })
    }

    @Test
    fun `events are buffered during SYNCING and flushed on SYNC_ACK`() {
        val sid = "s1"
        val pid = "p1"
        val session = mock<WebSocketSession>()
        whenever(session.id).thenReturn(sid)
        whenever(session.isOpen).thenReturn(true)

        messagingService.addSession(session)
        messagingService.registerPlayer(sid, pid) // syncState = SYNCING

        val event = GameEvent.GameOver("DWARVES")
        messagingService.sendEventToPlayer(pid, event)

        // Verify NOT sent
        verify(session, never()).sendMessage(any())

        // Mark as SYNCED
        messagingService.setSessionSynced(sid)

        // Verify sent (flushed)
        verify(session, atLeastOnce()).sendMessage(argThat { msg -> (msg as TextMessage).payload.contains("GAME_OVER") })
        // And Verify SYNC_COMPLETE was sent
        verify(session, atLeastOnce()).sendMessage(argThat { msg -> (msg as TextMessage).payload.contains("SYNC_COMPLETE") })
    }

    @Test
    fun `lobby locks are independent and consistent`() {
        val lock1 = messagingService.getLobbyLock("L1")
        val lock2 = messagingService.getLobbyLock("L2")
        val lock1Again = messagingService.getLobbyLock("L1")
        
        assert(lock1 === lock1Again)
        assert(lock1 !== lock2)
    }

    @Test
    fun `registerPlayer closes old session for same playerId`() {
        val session1 = mock<WebSocketSession>()
        val session2 = mock<WebSocketSession>()
        whenever(session1.id).thenReturn("s1")
        whenever(session2.id).thenReturn("s2")
        whenever(session1.isOpen).thenReturn(true)

        messagingService.addSession(session1)
        messagingService.registerPlayer("s1", "p1")
        
        messagingService.addSession(session2)
        messagingService.registerPlayer("s2", "p1") // Same player, new session

        verify(session1).close(CloseStatus.SESSION_NOT_RELIABLE)
        assertEquals(1, messagingService.getActiveSessionsCount())
    }

    @Test
    fun `removeSession cleans up all mappings`() {
        val session = mock<WebSocketSession>()
        whenever(session.id).thenReturn("s1")
        messagingService.addSession(session)
        messagingService.registerPlayer("s1", "p1")
        messagingService.joinLobbyGroup("s1", "L1")
        
        assertEquals(1, messagingService.getActiveSessionsCount())
        assertEquals(1, messagingService.getRegisteredPlayersCount())
        
        messagingService.removeSession(session)
        
        assertEquals(0, messagingService.getActiveSessionsCount())
        assertEquals(0, messagingService.getRegisteredPlayersCount())
        assertEquals(null, messagingService.getLobbyCodeForSession("s1"))
    }

    @Test
    fun `updatePlayerActivity and getPlayerLastSeen work`() {
        val before = System.currentTimeMillis()
        messagingService.updatePlayerActivity("p1")
        val activity = messagingService.getPlayerLastSeen("p1")
        val after = System.currentTimeMillis()
        
        assert(activity in before..after)
        assertEquals(0L, messagingService.getPlayerLastSeen("unknown"))
    }

    @Test
    fun `sendEventToLobby sends message to all sessions in group`() {
        val s1 = mock<WebSocketSession>()
        val s2 = mock<WebSocketSession>()
        whenever(s1.id).thenReturn("sid1")
        whenever(s2.id).thenReturn("sid2")
        whenever(s1.isOpen).thenReturn(true)
        whenever(s2.isOpen).thenReturn(true)

        messagingService.addSession(s1)
        messagingService.addSession(s2)
        messagingService.joinLobbyGroup("sid1", "L1")
        messagingService.joinLobbyGroup("sid2", "L1")

        messagingService.sendEventToLobby("L1", GameEvent.LobbyLeft())

        verify(s1).sendMessage(any())
        verify(s2).sendMessage(any())
    }

    @Test
    fun `leaveLobbyGroup removes session from group`() {
        val s1 = mock<WebSocketSession>()
        whenever(s1.id).thenReturn("sid1")
        whenever(s1.isOpen).thenReturn(true)
        messagingService.addSession(s1)
        messagingService.joinLobbyGroup("sid1", "L1")
        
        messagingService.leaveLobbyGroup("sid1", "L1")
        messagingService.sendEventToLobby("L1", GameEvent.LobbyLeft())
        
        verify(s1, never()).sendMessage(any())
    }

    @Test
    fun `sendEventToSession sends message directly`() {
        val s1 = mock<WebSocketSession>()
        whenever(s1.id).thenReturn("sid1")
        whenever(s1.isOpen).thenReturn(true)
        messagingService.addSession(s1)
        
        messagingService.sendEventToSession("sid1", GameEvent.SyncComplete())
        verify(s1).sendMessage(any())
    }

    @Test
    fun `clearLobbyMappings removes lobby entries`() {
        messagingService.joinLobbyGroup("s1", "L1")
        messagingService.clearLobbyMappings("L1")

        val s1 = mock<WebSocketSession>()
        messagingService.sendEventToLobby("L1", GameEvent.LobbyLeft())
        verify(s1, never()).sendMessage(any())
    }
}
