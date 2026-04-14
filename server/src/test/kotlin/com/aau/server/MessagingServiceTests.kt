package com.aau.server

import com.aau.saboteur.model.WsMessage
import com.aau.server.service.MessagingService
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
    fun `broadcast sends message to all open sessions`() {
        val session1 = mock(WebSocketSession::class.java)
        val session2 = mock(WebSocketSession::class.java)
        `when`(session1.isOpen).thenReturn(true)
        `when`(session2.isOpen).thenReturn(true)
        `when`(session1.id).thenReturn("s1")
        `when`(session2.id).thenReturn("s2")

        messagingService.addSession(session1)
        messagingService.addSession(session2)

        val type = "TEST_TYPE"
        val data = "test-data"
        messagingService.broadcast(type, data)

        val expectedPayload = objectMapper.writeValueAsString(WsMessage(type, data))
        val expectedMessage = TextMessage(expectedPayload)

        verify(session1).sendMessage(expectedMessage)
        verify(session2).sendMessage(expectedMessage)
    }

    @Test
    fun `broadcast does not send to closed sessions`() {
        val session1 = mock(WebSocketSession::class.java)
        `when`(session1.isOpen).thenReturn(false)
        `when`(session1.id).thenReturn("s1")

        messagingService.addSession(session1)
        messagingService.broadcast("TYPE", "DATA")

        verify(session1, never()).sendMessage(any())
    }

    @Test
    fun `broadcast handles exception during sendMessage`() {
        val session1 = mock(WebSocketSession::class.java)
        `when`(session1.isOpen).thenReturn(true)
        `when`(session1.id).thenReturn("s1")
        `when`(session1.sendMessage(any())).thenThrow(RuntimeException("Socket error"))

        messagingService.addSession(session1)
        
        // This should not throw an exception out of the broadcast method
        messagingService.broadcast("TYPE", "DATA")

        verify(session1).sendMessage(any())
    }

    @Test
    fun `removeSession prevents future broadcasts to that session`() {
        val session1 = mock(WebSocketSession::class.java)
        `when`(session1.isOpen).thenReturn(true)
        `when`(session1.id).thenReturn("s1")

        messagingService.addSession(session1)
        messagingService.removeSession(session1)
        
        messagingService.broadcast("TYPE", "DATA")

        verify(session1, never()).sendMessage(any())
    }

    @Test
    fun `sendToPlayer broadcasts with prefixed type`() {
        val session1 = mock(WebSocketSession::class.java)
        `when`(session1.isOpen).thenReturn(true)
        `when`(session1.id).thenReturn("s1")
        messagingService.addSession(session1)

        val playerId = "p1"
        val type = "PLAYER_DATA"
        val data = "role-info"
        
        messagingService.sendToPlayer(playerId, type, data)

        val expectedPayload = objectMapper.writeValueAsString(WsMessage("${type}_$playerId", data))
        val expectedMessage = TextMessage(expectedPayload)

        verify(session1).sendMessage(expectedMessage)
    }
}
