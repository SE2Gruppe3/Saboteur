package com.aau.server.websocket.command

import org.springframework.web.socket.WebSocketSession
import kotlin.reflect.KClass

/**
 * Base interface for all incoming WebSocket commands.
 */
interface Command

/**
 * Interface for handling a specific type of command.
 */
interface CommandHandler<T : Command> {
    val commandType: String
    val commandClass: KClass<T>
    fun handle(session: WebSocketSession, command: T)
}
