package com.aau.server

import com.aau.saboteur.model.WsMessage
import com.aau.server.service.MessagingService
import com.aau.server.websocket.event.GameEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
    fun `broadcastEvent sends message to all open sessions`() {
        val session1 = mock(WebSocketSession::class.java)
        val session2 = mock(WebSocketSession::class.java)
        `when`(session1.isOpen).thenReturn(true)
        `when`(session2.isOpen).thenReturn(true)
        `when`(session1.id).thenReturn("s1")
        `when`(session2.id).thenReturn("s2")

        messagingService.addSession(session1)
        messagingService.addSession(session2)

        val event = GameEvent.ErrorEvent("test-error")
        messagingService.broadcastEvent(event)

        val expectedPayload = objectMapper.writeValueAsString(WsMessage(event.type, event.payload))
        
        verify(session1).sendMessage(argThat { it.payload == expectedPayload })
        verify(session2).sendMessage(argThat { it.payload == expectedPayload })
    }

    @Test
    fun `removeSession prevents future broadcasts`() {
        val session1 = mock(WebSocketSession::class.java)
        `when`(session1.isOpen).thenReturn(true)
        `when`(session1.id).thenReturn("s1")

        messagingService.addSession(session1)
        messagingService.removeSession(session1)
        
        messagingService.broadcastEvent(GameEvent.ErrorEvent("data"))
        verify(session1, never()).sendMessage(any())
    }

    @Test
    fun `sendEventToLobby sends only to lobby members`() {
        val s1 = mock(WebSocketSession::class.java)
        val s2 = mock(WebSocketSession::class.java)
        `when`(s1.isOpen).thenReturn(true)
        `when`(s2.isOpen).thenReturn(true)
        `when`(s1.id).thenReturn("s1")
        `when`(s2.id).thenReturn("s2")

        messagingService.addSession(s1)
        messagingService.addSession(s2)
        messagingService.joinLobbyGroup("s1", "L1")

        val event = GameEvent.ErrorEvent("data")
        messagingService.sendEventToLobby("L1", event)
        
        verify(s1).sendMessage(any())
        verify(s2, never()).sendMessage(any())
    }

    @Test
    fun `sendEventToPlayer sends to all player sessions`() {
        val s1 = mock(WebSocketSession::class.java)
        val s2 = mock(WebSocketSession::class.java)
        `when`(s1.isOpen).thenReturn(true)
        `when`(s2.isOpen).thenReturn(true)
        `when`(s1.id).thenReturn("s1")
        `when`(s2.id).thenReturn("s2")
        
        messagingService.addSession(s1)
        messagingService.addSession(s2)

        val playerId = "p1"
        messagingService.registerPlayer("s1", playerId)
        messagingService.registerPlayer("s2", playerId)
        
        val event = GameEvent.ErrorEvent("data")
        messagingService.sendEventToPlayer(playerId, event)

        verify(s1).sendMessage(any())
        verify(s2).sendMessage(any())
    }
}
