package com.aau.server

import com.aau.saboteur.model.WsMessage
import com.aau.server.service.MessagingService
import com.aau.server.service.SessionSyncState
import com.aau.server.websocket.event.GameEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

class MessagingServiceTests {

    private lateinit var messagingService: MessagingService
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setup() {
        messagingService = MessagingService(objectMapper)
    }

    @Test
    fun `broadcastEvent sends message to all open synced sessions`() {
        val session1 = mock(WebSocketSession::class.java)
        val session2 = mock(WebSocketSession::class.java)
        `when`(session1.isOpen).thenReturn(true)
        `when`(session2.isOpen).thenReturn(true)
        `when`(session1.id).thenReturn("s1")
        `when`(session2.id).thenReturn("s2")

        messagingService.addSession(session1)
        messagingService.addSession(session2)
        
        // Sessions are SYNCING by default when registered, 
        // so let's mark them as SYNCED to test normal broadcast
        messagingService.registerPlayer("s1", "p1")
        messagingService.registerPlayer("s2", "p2")
        messagingService.setSessionSynced("s1")
        messagingService.setSessionSynced("s2")

        val event = GameEvent.ErrorEvent("test-error")
        messagingService.broadcastEvent(event)

        val expectedPayload = objectMapper.writeValueAsString(WsMessage(event.type, event.payload))
        
        verify(session1, atLeastOnce()).sendMessage(argThat { msg: TextMessage -> msg.payload == expectedPayload })
        verify(session2, atLeastOnce()).sendMessage(argThat { msg: TextMessage -> msg.payload == expectedPayload })
    }

    @Test
    fun `events are buffered during SYNCING and flushed on SYNC_ACK`() {
        val sid = "s1"
        val pid = "p1"
        val session = mock(WebSocketSession::class.java)
        `when`(session.id).thenReturn(sid)
        `when`(session.isOpen).thenReturn(true)

        messagingService.addSession(session)
        messagingService.registerPlayer(sid, pid) // syncState = SYNCING

        val event = GameEvent.GameOver("DWARVES")
        messagingService.sendEventToPlayer(pid, event)

        // Verify NOT sent
        verify(session, never()).sendMessage(argThat { msg: TextMessage -> msg.payload.contains("GAME_OVER") })

        // Mark as SYNCED
        messagingService.setSessionSynced(sid)

        // Verify sent (flushed)
        verify(session).sendMessage(argThat { msg: TextMessage -> msg.payload.contains("GAME_OVER") })
        // And Verify SYNC_COMPLETE was sent
        verify(session).sendMessage(argThat { msg: TextMessage -> msg.payload.contains("SYNC_COMPLETE") })
    }

    @Test
    fun `lobby locks are independent and consistent`() {
        val lock1 = messagingService.getLobbyLock("L1")
        val lock2 = messagingService.getLobbyLock("L2")
        val lock1Again = messagingService.getLobbyLock("L1")
        
        assert(lock1 === lock1Again)
        assert(lock1 !== lock2)
    }
}
