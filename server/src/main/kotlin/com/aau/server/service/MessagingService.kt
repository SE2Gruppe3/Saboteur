package com.aau.server.service

import com.aau.saboteur.model.WsMessage
import com.aau.server.websocket.event.GameEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock

enum class SessionSyncState { SYNCING, SYNCED }

@Service
class MessagingService(private val objectMapper: ObjectMapper) {
    private val logger = LoggerFactory.getLogger(MessagingService::class.java)

    private val sessionsById = ConcurrentHashMap<String, WebSocketSession>()
    private val playerToSession = ConcurrentHashMap<String, String>()
    private val sessionToPlayer = ConcurrentHashMap<String, String>()
    private val lobbyToSessions = ConcurrentHashMap<String, MutableSet<String>>()
    private val sessionToLobby = ConcurrentHashMap<String, String>()
    private val playerLastSeen = ConcurrentHashMap<String, Long>()
    private val sessionSyncState = ConcurrentHashMap<String, SessionSyncState>()
    private val eventBuffer = ConcurrentHashMap<String, ConcurrentLinkedQueue<TextMessage>>()
    private val lobbyLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun getLobbyLock(lobbyCode: String): ReentrantLock = lobbyLocks.getOrPut(lobbyCode) { ReentrantLock() }

    fun addSession(session: WebSocketSession) {
        sessionsById[session.id] = session
    }

    fun removeSession(session: WebSocketSession) {
        val sid = session.id
        sessionToPlayer.remove(sid)?.let { playerToSession.remove(it) }
        sessionToLobby.remove(sid)?.let { lobbyToSessions[it]?.remove(sid) }
        sessionSyncState.remove(sid)
        eventBuffer.remove(sid)
        sessionsById.remove(sid)
    }

    fun updatePlayerActivity(pid: String) {
        playerLastSeen[pid] = System.currentTimeMillis()
    }

    fun getPlayerLastSeen(pid: String): Long = playerLastSeen[pid] ?: 0

    fun registerPlayer(sessionId: String, playerId: String) {
        val oldSid = playerToSession[playerId]
        if (oldSid != null && oldSid != sessionId) {
            sessionsById[oldSid]?.close(CloseStatus.SESSION_NOT_RELIABLE)
            removeSession(sessionsById[oldSid] ?: return)
        }
        sessionToPlayer[sessionId] = playerId
        playerToSession[playerId] = sessionId
        sessionSyncState[sessionId] = SessionSyncState.SYNCING
        eventBuffer[sessionId] = ConcurrentLinkedQueue()
        updatePlayerActivity(playerId)
    }

    fun joinLobbyGroup(sessionId: String, lobbyCode: String) {
        sessionToLobby[sessionId] = lobbyCode
        lobbyToSessions.computeIfAbsent(lobbyCode) { ConcurrentHashMap.newKeySet() }.add(sessionId)
    }

    fun leaveLobbyGroup(sessionId: String, lobbyCode: String) {
        sessionToLobby.remove(sessionId)
        lobbyToSessions[lobbyCode]?.remove(sessionId)
    }

    fun setSessionSynced(sessionId: String) {
        val buffer = eventBuffer[sessionId] ?: return
        synchronized(buffer) {
            sessionSyncState[sessionId] = SessionSyncState.SYNCED
            while (buffer.isNotEmpty()) {
                buffer.poll()?.let { msg -> sessionsById[sessionId]?.let { sendMessageRaw(it, msg) } }
            }
        }
        sendEventToSession(sessionId, GameEvent.SyncComplete())
    }

    fun sendEventToLobby(lobbyCode: String, event: GameEvent) {
        executeAfterCommit {
            val msg = TextMessage(objectMapper.writeValueAsString(WsMessage(event.type, event.payload)))
            lobbyToSessions[lobbyCode]?.toList()?.forEach { sendToSessionInternal(it, msg) }
        }
    }

    fun sendEventToPlayer(playerId: String, event: GameEvent) {
        executeAfterCommit {
            val sid = playerToSession[playerId] ?: return@executeAfterCommit
            val msg = TextMessage(objectMapper.writeValueAsString(WsMessage(event.type, event.payload)))
            sendToSessionInternal(sid, msg)
        }
    }

    fun broadcastEvent(event: GameEvent) {
        executeAfterCommit {
            val msg = TextMessage(objectMapper.writeValueAsString(WsMessage(event.type, event.payload)))
            sessionsById.keys.forEach { sendToSessionInternal(it, msg) }
        }
    }

    private fun executeAfterCommit(action: () -> Unit) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() { action() }
            })
        } else {
            action()
        }
    }

    private fun sendToSessionInternal(sid: String, message: TextMessage) {
        val buffer = eventBuffer[sid]
        val session = sessionsById[sid] ?: return
        if (buffer != null) {
            synchronized(buffer) {
                if (sessionSyncState[sid] == SessionSyncState.SYNCING) buffer.add(message)
                else sendMessageRaw(session, message)
            }
        } else {
            sendMessageRaw(session, message)
        }
    }

    fun sendEventToSession(sessionId: String, event: GameEvent) {
        val session = sessionsById[sessionId] ?: return
        sendMessageRaw(session, TextMessage(objectMapper.writeValueAsString(WsMessage(event.type, event.payload))))
    }

    private fun sendMessageRaw(session: WebSocketSession, message: TextMessage) {
        if (session.isOpen) {
            try { synchronized(session) { session.sendMessage(message) } }
            catch (e: Exception) { logger.error("WS Send Error: {}", e.message) }
        }
    }

    fun getLobbyCodeForSession(sid: String) = sessionToLobby[sid]
    fun getPlayerIdForSession(sid: String) = sessionToPlayer[sid]
    fun getActiveSessionForPlayer(pid: String) = playerToSession[pid]
    fun clearLobbyMappings(code: String) { lobbyToSessions.remove(code); lobbyLocks.remove(code) }

    fun getActiveSessionsCount(): Int = sessionsById.size
    fun getRegisteredPlayersCount(): Int = playerToSession.size
}
