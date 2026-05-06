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
    fun `removeSession prevents future broadcasts and cleans up metadata`() {
        val session1 = mock(WebSocketSession::class.java)
        `when`(session1.isOpen).thenReturn(true)
        `when`(session1.id).thenReturn("s1")

        messagingService.addSession(session1)
        messagingService.joinLobbyGroup("s1", "L1")
        messagingService.registerPlayer("s1", "P1")
        
        messagingService.removeSession(session1)
        
        messagingService.broadcast("TYPE", "DATA")
        verify(session1, never()).sendMessage(any())
        
        assert(messagingService.getLobbyCodeForSession("s1") == null)
        assert(messagingService.getPlayerIdForSession("s1") == null)
    }

    @Test
    fun `removeSession handles session not in a lobby`() {
        val session = mock(WebSocketSession::class.java)
        `when`(session.id).thenReturn("s1")
        messagingService.addSession(session)
        
        // Should not throw
        messagingService.removeSession(session)
    }

    @Test
    fun `removeSession cleans up lobby mappings only when last session leaves`() {
        val s1 = mock(WebSocketSession::class.java)
        val s2 = mock(WebSocketSession::class.java)
        `when`(s1.id).thenReturn("s1")
        `when`(s2.id).thenReturn("s2")
        `when`(s1.isOpen).thenReturn(true)
        `when`(s2.isOpen).thenReturn(true)
        
        messagingService.addSession(s1)
        messagingService.addSession(s2)
        messagingService.joinLobbyGroup("s1", "L1")
        messagingService.joinLobbyGroup("s2", "L1")
        
        messagingService.removeSession(s1)
        
        // Lobby L1 should still exist because s2 is in it
        messagingService.broadcastToLobby("L1", "TYPE", "DATA")
        verify(s2).sendMessage(any())
    }

    @Test
    fun `joinLobbyGroup and leaveLobbyGroup manage lobby membership`() {
        val session1 = mock(WebSocketSession::class.java)
        `when`(session1.isOpen).thenReturn(true)
        `when`(session1.id).thenReturn("s1")
        messagingService.addSession(session1)

        messagingService.joinLobbyGroup("s1", "L1")
        assert(messagingService.getLobbyCodeForSession("s1") == "L1")

        // Switch lobby
        messagingService.joinLobbyGroup("s1", "L2")
        assert(messagingService.getLobbyCodeForSession("s1") == "L2")

        messagingService.leaveLobbyGroup("s1", "L2")
        assert(messagingService.getLobbyCodeForSession("s1") == null)
    }

    @Test
    fun `broadcastToLobby skips sessionIds not in sessionsById`() {
        // Add a sessionId to a lobby without calling addSession
        messagingService.joinLobbyGroup("unknown", "L1")
        
        // Should not throw NPE or anything
        messagingService.broadcastToLobby("L1", "TYPE", "DATA")
    }

    @Test
    fun `broadcastToLobby sends only to lobby members`() {
        val s1 = mock(WebSocketSession::class.java)
        val s2 = mock(WebSocketSession::class.java)
        `when`(s1.isOpen).thenReturn(true)
        `when`(s2.isOpen).thenReturn(true)
        `when`(s1.id).thenReturn("s1")
        `when`(s2.id).thenReturn("s2")

        messagingService.addSession(s1)
        messagingService.addSession(s2)
        messagingService.joinLobbyGroup("s1", "L1")

        messagingService.broadcastToLobby("L1", "TYPE", "DATA")
        verify(s1).sendMessage(any())
        verify(s2, never()).sendMessage(any())
        
        // Non-existent lobby returns immediately
        messagingService.broadcastToLobby("L-NONE", "TYPE", "DATA")
    }

    @Test
    fun `sendToPlayer broadcasts with prefixed type to all player sessions`() {
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
        
        val type = "PLAYER_DATA"
        val data = "role-info"
        
        messagingService.sendToPlayer(playerId, type, data)

        val expectedPayload = objectMapper.writeValueAsString(WsMessage("${type}_$playerId", data))
        val expectedMessage = TextMessage(expectedPayload)

        verify(s1).sendMessage(expectedMessage)
        verify(s2).sendMessage(expectedMessage)
    }

    @Test
    fun `sendToPlayer does nothing if player has no sessions`() {
        messagingService.sendToPlayer("nonExistent", "TYPE", "DATA")
    }

    @Test
    fun `sendToSession returns when sessionId is unknown`() {
        messagingService.sendToSession("unknown", "TYPE", "DATA")
    }

    @Test
    fun `sendToSession does not send when session is closed`() {
        val session = mock(WebSocketSession::class.java)
        `when`(session.isOpen).thenReturn(false)
        `when`(session.id).thenReturn("s1")

        messagingService.addSession(session)
        messagingService.sendToSession("s1", "TYPE", "DATA")

        verify(session, never()).sendMessage(any())
    }

    @Test
    fun `sendToSession sends when session is open`() {
        val session = mock(WebSocketSession::class.java)
        `when`(session.isOpen).thenReturn(true)
        `when`(session.id).thenReturn("s1")

        messagingService.addSession(session)

        val type = "TYPE"
        val data = "DATA"

        messagingService.sendToSession("s1", type, data)

        val expectedPayload = objectMapper.writeValueAsString(WsMessage(type, data))
        val expectedMessage = TextMessage(expectedPayload)

        verify(session).sendMessage(expectedMessage)
    }

    @Test
    fun `sendToSession handles exception during sendMessage`() {
        val session = mock(WebSocketSession::class.java)
        `when`(session.isOpen).thenReturn(true)
        `when`(session.id).thenReturn("s1")
        `when`(session.sendMessage(any())).thenThrow(RuntimeException("Socket error"))

        messagingService.addSession(session)
        messagingService.sendToSession("s1", "TYPE", "DATA")

        verify(session).sendMessage(any())
    }

    @Test
    fun `sendToSessions skips unknown sessionIds`() {
        messagingService.sendToSessions(listOf("unknown"), "TYPE", "DATA")
    }

    @Test
    fun `sendToSessions sends to each sessionId`() {
        val s1 = mock(WebSocketSession::class.java)
        val s2 = mock(WebSocketSession::class.java)
        `when`(s1.isOpen).thenReturn(true)
        `when`(s2.isOpen).thenReturn(true)
        `when`(s1.id).thenReturn("s1")
        `when`(s2.id).thenReturn("s2")

        messagingService.addSession(s1)
        messagingService.addSession(s2)

        messagingService.sendToSessions(listOf("s1", "s2"), "TYPE", "DATA")

        verify(s1).sendMessage(any())
        verify(s2).sendMessage(any())
    }
}
