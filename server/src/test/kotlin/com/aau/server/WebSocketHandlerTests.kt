package com.aau.server

import com.aau.saboteur.model.*
import com.aau.server.service.MessagingService
import com.aau.server.websocket.WebSocketHandler
import com.aau.server.websocket.command.CommandDispatcher
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

class WebSocketHandlerTests {

    private lateinit var messagingService: MessagingService
    private lateinit var commandDispatcher: CommandDispatcher
    private lateinit var objectMapper: ObjectMapper
    private lateinit var handler: WebSocketHandler
    private lateinit var session: WebSocketSession

    @BeforeEach
    fun setup() {
        messagingService = mock(MessagingService::class.java)
        commandDispatcher = mock(CommandDispatcher::class.java)
        objectMapper = jacksonObjectMapper()

        handler = WebSocketHandler(objectMapper, messagingService, commandDispatcher)

        session = mock(WebSocketSession::class.java)
        `when`(session.isOpen).thenReturn(true)
        `when`(session.id).thenReturn("test-session")
    }

    // Helpers for Mockito with Kotlin non-nullable types
    private fun <T> anyK(): T = any() ?: null as T
    private fun <T> eqK(value: T): T = eq(value) ?: value

    @Test
    fun `afterConnectionEstablished delegates to messagingService`() {
        handler.afterConnectionEstablished(session)
        verify(messagingService).addSession(session)
    }

    @Test
    fun `afterConnectionClosed delegates to messagingService`() {
        handler.afterConnectionClosed(session, CloseStatus.NORMAL)
        verify(messagingService).removeSession(session)
    }

    @Test
    fun `handleTextMessage dispatches command via dispatcher`() {
        val type = "REGISTER"
        val data = mapOf("playerId" to "p1", "lobbyCode" to "1234")
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to type, "data" to data)))

        handler.handleTextMessage(session, message)

        verify(commandDispatcher).dispatch(anyK(), eqK(type), anyK())
    }

    @Test
    fun `handleTextMessage handles dispatcher exception by sending ERROR`() {
        val type = "REGISTER"
        val message = TextMessage(objectMapper.writeValueAsString(mapOf("type" to type, "data" to emptyMap<String, String>())))

        `when`(commandDispatcher.dispatch(anyK(), anyString(), anyK())).thenThrow(RuntimeException("Dispatch failed"))

        handler.handleTextMessage(session, message)

        verify(session).sendMessage(argThat { msg -> 
            val payload = msg?.payload?.toString() ?: ""
            payload.contains("ERROR") && payload.contains("Dispatch failed") 
        })
    }
}
