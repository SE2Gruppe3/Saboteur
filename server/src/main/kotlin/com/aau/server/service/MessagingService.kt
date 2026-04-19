package com.aau.server.service

import com.aau.saboteur.model.WsMessage
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class MessagingService(private val objectMapper: ObjectMapper) {
    private val logger = LoggerFactory.getLogger(MessagingService::class.java)

    // existing broadcast list
    private val sessions = CopyOnWriteArrayList<WebSocketSession>()

    // new: lookup by sessionId for targeted messaging
    private val sessionsById = ConcurrentHashMap<String, WebSocketSession>()

    fun addSession(session: WebSocketSession) {
        sessions.add(session)
        sessionsById[session.id] = session
    }

    fun removeSession(session: WebSocketSession) {
        sessions.remove(session)
        sessionsById.remove(session.id)
    }

    fun broadcast(type: String, data: Any) {
        val message = TextMessage(objectMapper.writeValueAsString(WsMessage(type, data)))
        sessions.forEach { session ->
            if (session.isOpen) {
                try {
                    session.sendMessage(message)
                } catch (e: Exception) {
                    logger.error("Error sending message to session {}: {}", session.id, e.message)
                }
            }
        }
    }

    fun sendToSession(sessionId: String, type: String, data: Any) {
        val session = sessionsById[sessionId] ?: return
        if (!session.isOpen) return

        val message = TextMessage(objectMapper.writeValueAsString(WsMessage(type, data)))
        try {
            session.sendMessage(message)
        } catch (e: Exception) {
            logger.error("Error sending message to session {}: {}", session.id, e.message)
        }
    }

    fun sendToSessions(sessionIds: Collection<String>, type: String, data: Any) {
        sessionIds.forEach { id -> sendToSession(id, type, data) }
    }

    // keep existing behavior for backwards compatibility
    fun sendToPlayer(playerId: String, type: String, data: Any) {
        broadcast("${type}_$playerId", data)
    }
}