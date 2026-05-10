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

    private val sessions = CopyOnWriteArrayList<WebSocketSession>()
    private val sessionsById = ConcurrentHashMap<String, WebSocketSession>()

    private val sessionToLobby = ConcurrentHashMap<String, String>()
    private val lobbyToSessions = ConcurrentHashMap<String, MutableSet<String>>()
    private val sessionToPlayer = ConcurrentHashMap<String, String>()

    fun addSession(session: WebSocketSession) {
        sessions.add(session)
        sessionsById[session.id] = session
    }

    fun removeSession(session: WebSocketSession) {
        sessions.remove(session)
        sessionsById.remove(session.id)
        sessionToPlayer.remove(session.id)

        val lobbyCode = sessionToLobby.remove(session.id)
        if (lobbyCode != null) {
            lobbyToSessions[lobbyCode]?.remove(session.id)
            if (lobbyToSessions[lobbyCode]?.isEmpty() == true) {
                lobbyToSessions.remove(lobbyCode)
            }
        }
    }

    fun joinLobbyGroup(sessionId: String, lobbyCode: String) {
        val oldLobby = sessionToLobby[sessionId]
        if (oldLobby != null) {
            lobbyToSessions[oldLobby]?.remove(sessionId)
        }

        sessionToLobby[sessionId] = lobbyCode
        lobbyToSessions.computeIfAbsent(lobbyCode) { ConcurrentHashMap.newKeySet() }.add(sessionId)
    }

    fun leaveLobbyGroup(sessionId: String, lobbyCode: String) {
        sessionToLobby.remove(sessionId)
        lobbyToSessions[lobbyCode]?.remove(sessionId)
        if (lobbyToSessions[lobbyCode]?.isEmpty() == true) {
            lobbyToSessions.remove(lobbyCode)
        }
    }

    fun registerPlayer(sessionId: String, playerId: String) {
        sessionToPlayer[sessionId] = playerId
    }

    fun getLobbyCodeForSession(sessionId: String): String? = sessionToLobby[sessionId]

    fun getPlayerIdForSession(sessionId: String): String? = sessionToPlayer[sessionId]

    fun broadcast(type: String, data: Any) {
        val message = TextMessage(objectMapper.writeValueAsString(WsMessage(type, data)))
        sessions.forEach { session ->
            sendMessage(session, message)
        }
    }

    fun broadcastToLobby(lobbyCode: String, type: String, data: Any) {
        val sessionIds = lobbyToSessions[lobbyCode]?.toSet() ?: return
        val message = TextMessage(objectMapper.writeValueAsString(WsMessage(type, data)))
        sessionIds.forEach { sessionId ->
            sessionsById[sessionId]?.let { sendMessage(it, message) }
        }
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

    fun sendToSession(sessionId: String, type: String, data: Any) {
        val session = sessionsById[sessionId] ?: return
        val message = TextMessage(objectMapper.writeValueAsString(WsMessage(type, data)))
        sendMessage(session, message)
    }

    fun sendToSessions(sessionIds: List<String>, type: String, data: Any) {
        val message = TextMessage(objectMapper.writeValueAsString(WsMessage(type, data)))
        sessionIds.forEach { sessionId ->
            sessionsById[sessionId]?.let { sendMessage(it, message) }
        }
    }

    fun sendToPlayer(playerId: String, type: String, data: Any) {
        val sessionIds = sessionToPlayer
            .filterValues { it == playerId }
            .keys
            .toSet()

        val messageType = "${type}_$playerId"
        val message = TextMessage(objectMapper.writeValueAsString(WsMessage(messageType, data)))

        sessionIds.forEach { sessionId ->
            sessionsById[sessionId]?.let { sendMessage(it, message) }
        }
    }
}
