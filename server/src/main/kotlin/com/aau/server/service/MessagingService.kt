package com.aau.server.service

import com.aau.saboteur.model.WsMessage
import com.aau.server.websocket.event.GameEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

@Service
class MessagingService(private val objectMapper: ObjectMapper) {
    private val logger = LoggerFactory.getLogger(MessagingService::class.java)

    private val sessionsById = ConcurrentHashMap<String, WebSocketSession>()
    private val sessionToLobby = ConcurrentHashMap<String, String>()
    private val lobbyToSessions = ConcurrentHashMap<String, MutableSet<String>>()
    private val sessionToPlayer = ConcurrentHashMap<String, String>()

    fun addSession(session: WebSocketSession) {
        sessionsById[session.id] = session
    }

    fun removeSession(session: WebSocketSession) {
        sessionsById.remove(session.id)
        sessionToPlayer.remove(session.id)
        val lobbyCode = sessionToLobby.remove(session.id)
        if (lobbyCode != null) {
            lobbyToSessions[lobbyCode]?.remove(session.id)
        }
    }

    fun joinLobbyGroup(sessionId: String, lobbyCode: String) {
        sessionToLobby[sessionId] = lobbyCode
        lobbyToSessions.computeIfAbsent(lobbyCode) { ConcurrentHashMap.newKeySet() }.add(sessionId)
    }

    fun leaveLobbyGroup(sessionId: String, lobbyCode: String) {
        sessionToLobby.remove(sessionId)
        lobbyToSessions[lobbyCode]?.remove(sessionId)
    }

    fun registerPlayer(sessionId: String, playerId: String) {
        sessionToPlayer[sessionId] = playerId
    }

    fun getLobbyCodeForSession(sessionId: String): String? = sessionToLobby[sessionId]
    fun getPlayerIdForSession(sessionId: String): String? = sessionToPlayer[sessionId]

    fun sendEventToLobby(lobbyCode: String, event: GameEvent) {
        val message = TextMessage(objectMapper.writeValueAsString(WsMessage(event.type, event.payload)))
        lobbyToSessions[lobbyCode]?.forEach { sessionId ->
            sessionsById[sessionId]?.let { sendMessage(it, message) }
        }
    }

    fun sendEventToPlayer(playerId: String, event: GameEvent) {
        val sessionIds = sessionToPlayer.filterValues { it == playerId }.keys
        val message = TextMessage(objectMapper.writeValueAsString(WsMessage(event.type, event.payload)))
        sessionIds.forEach { sessionId ->
            sessionsById[sessionId]?.let { sendMessage(it, message) }
        }
    }

    fun broadcastEvent(event: GameEvent) {
        val message = TextMessage(objectMapper.writeValueAsString(WsMessage(event.type, event.payload)))
        sessionsById.values.forEach { sendMessage(it, message) }
    }

    fun sendEventToSession(sessionId: String, event: GameEvent) {
        val session = sessionsById[sessionId] ?: return
        val message = TextMessage(objectMapper.writeValueAsString(WsMessage(event.type, event.payload)))
        sendMessage(session, message)
    }

    private fun sendMessage(session: WebSocketSession, message: TextMessage) {
        if (session.isOpen) {
            try {
                session.sendMessage(message)
            } catch (e: Exception) {
                logger.error("Error sending message to session {}: {}", session.id, e.message)
            }
        }
    }
}
