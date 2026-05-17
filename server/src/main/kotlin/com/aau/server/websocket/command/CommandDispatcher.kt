package com.aau.server.websocket.command

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

@Component
class CommandDispatcher(
    private val objectMapper: ObjectMapper,
    handlers: List<CommandHandler<*>>
) {
    private val logger = LoggerFactory.getLogger(CommandDispatcher::class.java)
    
    private val handlerRegistry: Map<String, CommandHandler<*>> = handlers.associateBy { it.commandType }

    fun dispatch(session: WebSocketSession, type: String, data: JsonNode) {
        val handler = handlerRegistry[type] ?: run {
            logger.warn("No handler registered for command type: {}", type)
            return
        }

        try {
            val command = objectMapper.treeToValue(data, handler.commandClass.java)
                ?: throw IllegalArgumentException("Could not parse command data for type $type")
            
            @Suppress("UNCHECKED_CAST")
            (handler as CommandHandler<Command>).handle(session, command)
        } catch (e: Exception) {
            logger.error("Error dispatching command {}: {}", type, e.message)
            throw e
        }
    }
}
