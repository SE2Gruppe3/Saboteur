package com.aau.server.controller

import com.aau.saboteur.model.*
import com.aau.server.service.SessionService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * REST API tests for [SessionController].
 * 
 * Verifies that the endpoints correctly delegate to [SessionService]
 * and return the expected HTTP status codes and JSON structures.
 */
@WebMvcTest(SessionController::class)
class SessionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var sessionService: SessionService

    /**
     * Verifies that /reconnect returns 200 OK when the session and player are valid.
     */
    @Test
    fun `reconnect should return session info if player is in session`() {
        val sessionId = "SESS12"
        val playerId = "PLAYER1"
        val sessionInfo = SessionInfo(sessionId = sessionId, players = listOf(Player(id = playerId, name = "Test")))

        `when`(sessionService.reconnect(playerId, sessionId)).thenReturn(sessionInfo)

        mockMvc.post("/api/sessions/reconnect") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(ReconnectRequest(playerId, sessionId))
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.sessionId") { value(sessionId) }
            }
    }

    /**
     * Verifies that /reconnect returns 401 Unauthorized when the player is not part of the session.
     */
    @Test
    fun `reconnect should return 401 if player is not in session`() {
        val sessionId = "SESS12"
        val playerId = "STRANGER"

        `when`(sessionService.reconnect(playerId, sessionId)).thenReturn(null)

        mockMvc.post("/api/sessions/reconnect") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(ReconnectRequest(playerId, sessionId))
        }
            .andExpect {
                status { isUnauthorized() }
            }
    }

    /**
     * Verifies that joining a non-existent session returns 404 Not Found.
     */
    @Test
    fun `join session should return 404 if session does not exist`() {
        val sessionId = "NONEXISTENT"
        `when`(sessionService.joinSession(sessionId, "Player")).thenReturn(null)

        mockMvc.post("/api/sessions/join") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(JoinSessionRequest(sessionId, "Player"))
        }
            .andExpect {
                status { isNotFound() }
            }
    }
}
