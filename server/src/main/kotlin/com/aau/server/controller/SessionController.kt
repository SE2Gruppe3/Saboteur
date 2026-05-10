package com.aau.server.controller

import com.aau.saboteur.model.JoinSessionRequest
import com.aau.saboteur.model.ReconnectRequest
import com.aau.saboteur.model.SessionInfo
import com.aau.server.service.SessionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST Controller for managing game sessions.
 * 
 * Provides endpoints for creating, joining, and reconnecting to sessions.
 * These endpoints are the primary entry points for the Android client's session logic.
 */
@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = ["*"])
class SessionController(private val sessionService: SessionService) {

    /**
     * Creates a new game session.
     * 
     * @param playerName The name of the host player.
     * @return 200 OK with the new [SessionInfo].
     */
    @PostMapping("/create")
    fun createSession(@RequestParam playerName: String): ResponseEntity<SessionInfo> {
        return ResponseEntity.ok(sessionService.createSession(playerName))
    }

    /**
     * Joins an existing game session using a session ID.
     * 
     * @param request Contains sessionId and playerName.
     * @return 200 OK with [SessionInfo] or 404 Not Found.
     */
    @PostMapping("/join")
    fun joinSession(@RequestBody request: JoinSessionRequest): ResponseEntity<SessionInfo> {
        val session = sessionService.joinSession(request.sessionId, request.playerName)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(session)
    }

    /**
     * Reconnects a player to an existing session after a disconnect.
     * This is a critical part of the Game State Recovery.
     * 
     * @param request Contains the stored playerId and sessionId.
     * @return 200 OK with [SessionInfo] if successful, or 401 Unauthorized.
     */
    @PostMapping("/reconnect")
    fun reconnect(@RequestBody request: ReconnectRequest): ResponseEntity<SessionInfo> {
        val session = sessionService.reconnect(request.playerId, request.sessionId)
            ?: return ResponseEntity.status(401).build()
        return ResponseEntity.ok(session)
    }

    /**
     * Retrieves the current state of a specific session.
     * 
     * @param sessionId The ID of the session.
     * @return 200 OK with [SessionInfo] or 404 Not Found.
     */
    @GetMapping("/{sessionId}")
    fun getSession(@PathVariable sessionId: String): ResponseEntity<SessionInfo> {
        val session = sessionService.getSession(sessionId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(session)
    }
}
