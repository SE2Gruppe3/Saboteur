package com.aau.server

import com.aau.server.websocket.WebSocketConfig
import com.aau.server.websocket.WebSocketHandler
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

class WebSocketConfigTest {

    @Test
    fun `registerWebSocketHandlers registers handler at correct path`() {
        val handler = mock(WebSocketHandler::class.java)
        val config = WebSocketConfig(handler)
        val registry = mock(WebSocketHandlerRegistry::class.java)
        val registration = mock(WebSocketHandlerRegistration::class.java)

        `when`(registry.addHandler(handler, "/ws")).thenReturn(registration)
        `when`(registration.setAllowedOrigins("*")).thenReturn(registration)

        config.registerWebSocketHandlers(registry)

        verify(registry).addHandler(handler, "/ws")
        verify(registration).setAllowedOrigins("*")
    }
}
