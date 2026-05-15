package com.aau.server.controller

import com.aau.saboteur.model.*
import com.aau.server.service.GameService
import com.aau.server.service.LobbyService
import com.aau.server.service.TurnManager
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(LobbyController::class)
class LobbyControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var lobbyService: LobbyService

    @MockitoBean
    private lateinit var gameService: GameService

    @MockitoBean
    private lateinit var turnManager: TurnManager

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `create lobby returns 200 and reconnect response`() {
        val request = LobbyCreateRequest("Alice", "p1")
        val lobbyState = LobbyState("1234", "p1", listOf(Player("p1", "Alice")), false)
        
        whenever(lobbyService.createLobby("Alice", "p1")).thenReturn(lobbyState)

        mockMvc.perform(post("/api/lobby/create")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myPlayerId").value("p1"))
            .andExpect(jsonPath("$.lobbyState.lobbyCode").value("1234"))
    }

    @Test
    fun `join lobby returns 200 and reconnect response`() {
        val request = LobbyJoinRequest("1234", "Bob", "p2")
        val lobbyState = LobbyState("1234", "p1", listOf(Player("p1", "Alice"), Player("p2", "Bob")), false)
        
        whenever(lobbyService.joinLobby("1234", "Bob", "p2")).thenReturn(lobbyState)

        mockMvc.perform(post("/api/lobby/join")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myPlayerId").value("p2"))
    }

    @Test
    fun `reconnect returns 200 when player is in lobby`() {
        val request = ReconnectRequest("p1", "1234")
        val lobbyState = LobbyState("1234", "p1", listOf(Player("p1", "Alice")), false)
        
        whenever(lobbyService.getLobby("1234")).thenReturn(lobbyState)

        mockMvc.perform(post("/api/lobby/reconnect")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myPlayerId").value("p1"))
    }

    @Test
    fun `reconnect returns 404 when lobby not found`() {
        val request = ReconnectRequest("p1", "1234")
        
        whenever(lobbyService.getLobby("1234")).thenThrow(IllegalArgumentException("Lobby not found"))

        mockMvc.perform(post("/api/lobby/reconnect")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `reconnect returns 403 when player not in lobby`() {
        val request = ReconnectRequest("p3", "1234")
        val lobbyState = LobbyState("1234", "p1", listOf(Player("p1", "Alice")), false)
        
        whenever(lobbyService.getLobby("1234")).thenReturn(lobbyState)

        mockMvc.perform(post("/api/lobby/reconnect")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden)
    }
}
