package com.aau.server.service

import com.aau.saboteur.model.*
import com.aau.server.model.GameSessionEntity
import com.aau.server.repository.GameSessionRepository
import jakarta.annotation.PostConstruct
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Service responsible for managing game sessions and ensuring persistence for Game State Recovery.
 *
 * This service acts as the central synchronization point between the volatile in-memory state
 * and the persistent database. 
 * 
 * ### Game State Recovery (Server-side):
 * On startup ([loadSessionsFromDb]), the service reads all sessions from the H2 database.
 * This ensures that if the server crashes or is restarted, players can seamlessly 
 * reconnect to their previous game state using the [reconnect] method.
 */
@Service
class SessionService(private val repository: GameSessionRepository) {

    /** Cache of active sessions for fast access during gameplay. */
    private val activeSessions = ConcurrentHashMap<String, SessionInfo>()
    
    /** JSON configuration for serializing complex game objects into database CLOBs. */
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Initializes the service by loading all existing sessions from the database.
     * 
     * Complex objects like [Player] lists and the [GameState] are stored as JSON strings
     * in the database (CLOB columns). This allows the schema to remain stable even
     * if the game logic evolves.
     */
    @PostConstruct
    fun loadSessionsFromDb() {
        val entities = repository.findAll()
        entities.forEach { entity ->
            // Reconstruct player list from JSON stored in DB
            val players = try {
                json.decodeFromString<List<Player>>(entity.playersJson)
            } catch (e: Exception) {
                emptyList()
            }
            
            // Reconstruct game state if present
            val gameState = try {
                if (entity.gameStateJson.isNotEmpty()) json.decodeFromString<GameState>(entity.gameStateJson) else null
            } catch (e: Exception) {
                null
            }
            
            activeSessions[entity.sessionId] = SessionInfo(
                sessionId = entity.sessionId,
                players = players,
                gameState = gameState,
                isStarted = entity.isStarted
            )
        }
        println("Recovery: Loaded ${activeSessions.size} sessions from persistent storage.")
    }

    /**
     * Creates a new game session with a unique 6-character ID.
     *
     * @param playerName The name of the player who becomes the host.
     * @return [SessionInfo] object representing the newly created session.
     */
    fun createSession(playerName: String): SessionInfo {
        val sessionId = UUID.randomUUID().toString().take(6).uppercase()
        val host = Player(id = UUID.randomUUID().toString(), name = playerName)
        val sessionInfo = SessionInfo(sessionId = sessionId, players = listOf(host))
        
        activeSessions[sessionId] = sessionInfo
        saveToDb(sessionInfo)
        return sessionInfo
    }

    /**
     * Adds a player to an existing lobby.
     * 
     * @param sessionId The ID of the session to join.
     * @param playerName Name of the joining player.
     * @return The updated [SessionInfo] if successful, or null if session is not found or already started.
     */
    fun joinSession(sessionId: String, playerName: String): SessionInfo? {
        val session = activeSessions[sessionId] ?: return null
        if (session.isStarted) return null
        
        val newPlayer = Player(id = UUID.randomUUID().toString(), name = playerName)
        val updatedSession = session.copy(players = session.players + newPlayer)
        
        activeSessions[sessionId] = updatedSession
        saveToDb(updatedSession)
        return updatedSession
    }

    /**
     * Handles Game State Recovery for a reconnected player.
     * 
     * Validates that the [playerId] is actually part of the session [sessionId].
     * If valid, returns the full state so the client can resume exactly where they left off.
     *
     * @param playerId Unique ID stored in the client's SharedPreferences.
     * @param sessionId Session ID to reconnect to.
     * @return [SessionInfo] containing the current game state, or null if authentication fails.
     */
    fun reconnect(playerId: String, sessionId: String): SessionInfo? {
        val session = activeSessions[sessionId] ?: return null
        return if (session.players.any { it.id == playerId }) {
            session
        } else {
            null
        }
    }

    /**
     * Updates the current game state and persists it to the database.
     * 
     * This ensures that even if the server restarts immediately after a move,
     * the board state is not lost.
     */
    fun updateGameState(sessionId: String, gameState: GameState) {
        val session = activeSessions[sessionId] ?: return
        val updatedSession = session.copy(gameState = gameState, isStarted = true)
        activeSessions[sessionId] = updatedSession
        saveToDb(updatedSession)
    }

    /**
     * Synchronizes a session state to the H2 database.
     */
    private fun saveToDb(sessionInfo: SessionInfo) {
        val entity = GameSessionEntity(
            sessionId = sessionInfo.sessionId,
            playersJson = json.encodeToString(sessionInfo.players),
            gameStateJson = sessionInfo.gameState?.let { json.encodeToString(it) } ?: "",
            isStarted = sessionInfo.isStarted
        )
        repository.save(entity)
    }
    
    /**
     * Retrieves a session by its ID.
     */
    fun getSession(sessionId: String): SessionInfo? = activeSessions[sessionId]
}
