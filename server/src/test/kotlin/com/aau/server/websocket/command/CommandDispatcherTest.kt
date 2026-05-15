package com.aau.server.websocket.command

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.web.socket.WebSocketSession
import kotlin.reflect.KClass

class CommandDispatcherTest {

    private lateinit var dispatcher: CommandDispatcher
    private lateinit var objectMapper: ObjectMapper
    private val session: WebSocketSession = mock()
    private val handler: CommandHandler<TestCommand> = mock()

    data class TestCommand(val message: String) : Command

    @BeforeEach
    @Suppress("UNCHECKED_CAST")
    fun setUp() {
        objectMapper = jacksonObjectMapper()
        whenever(handler.commandType).thenReturn("TEST_COMMAND")
        whenever(handler.commandClass).thenReturn(TestCommand::class as KClass<TestCommand>)
        
        dispatcher = CommandDispatcher(objectMapper, listOf(handler))
    }

    @Test
    fun `dispatch calls handler for registered type`() {
        val data = objectMapper.readTree("{\"message\": \"hello\"}")
        
        dispatcher.dispatch(session, "TEST_COMMAND", data)

        verify(handler).handle(eq(session), eq(TestCommand("hello")))
    }

    @Test
    fun `dispatch does nothing for unregistered type`() {
        val data = objectMapper.readTree("{}")
        
        dispatcher.dispatch(session, "UNKNOWN", data)
        
        verify(handler, never()).handle(any(), any())
    }

    @Test
    fun `dispatch throws exception if parsing fails`() {
        val data = objectMapper.readTree("{\"wrong\": 123}")
        
        assertThrows<Exception> {
            dispatcher.dispatch(session, "TEST_COMMAND", data)
        }
    }
}
