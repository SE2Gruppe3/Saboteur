package com.aau.server.controller

import com.aau.saboteur.model.*
import com.aau.server.repository.UserRepository
import com.aau.server.service.GameService
import com.aau.server.service.LobbyService
import com.aau.server.service.TurnManager
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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

    @MockitoBean
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `create lobby returns 200 and reconnect response`() {
        val request = LobbyCreateRequest("Alice", "p1", LobbyVisibility.PUBLIC)
        val lobbyState = LobbyState("1234", "p1", listOf(Player("p1", "Alice")), false)
        
        whenever(userRepository.findByPlayerId(any())).thenReturn(null)
        whenever(lobbyService.createLobby(any(), anyOrNull(), any(), any())).thenReturn(lobbyState)

        mockMvc.perform(post("/api/lobby/create")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myPlayerId").value("p1"))
    }

    @Test
    fun `join session returns 200 and reconnect response`() {
        val request = LobbyJoinRequest("1234", "Bob", "p2")
        val lobbyState = LobbyState("1234", "p1", listOf(Player("p1", "Alice"), Player("p2", "Bob")), false)
        
        whenever(userRepository.findByPlayerId(any())).thenReturn(null)
        whenever(lobbyService.joinLobby(any(), any(), anyOrNull(), any())).thenReturn(lobbyState)

        mockMvc.perform(post("/api/lobby/join")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myPlayerId").value("p2"))
    }

    @Test
    fun `list public lobbies returns list`() {
        val lobbies = listOf(LobbyState("1234", "p1", emptyList(), false, LobbyVisibility.PUBLIC))
        whenever(lobbyService.getPublicLobbies()).thenReturn(lobbies)

        mockMvc.perform(get("/api/lobby/list"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].lobbyCode").value("1234"))
    }

    @Test
    fun `reconnect returns 200 with game data if game started`() {
        val request = ReconnectRequest("p1", "1234")
        val lobbyState = LobbyState("1234", "p1", listOf(Player("p1", "Alice")), true)
        val gameState = GameState(emptyList(), "p1")
        val hand = listOf(TunnelCard("c1", CardType.PATH, emptySet()))
        
        whenever(lobbyService.getLobby("1234")).thenReturn(lobbyState)
        whenever(turnManager.getGameState("1234")).thenReturn(gameState)
        whenever(turnManager.getHands("1234")).thenReturn(mapOf("p1" to hand))
        whenever(gameService.getPlayer("1234", "p1")).thenReturn(Player("p1", "Alice", role = Role.SABOTEUR))

        mockMvc.perform(post("/api/lobby/reconnect")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gameState").exists())
            .andExpect(jsonPath("$.playerHand[0].id").value("c1"))
            .andExpect(jsonPath("$.playerRole").value("SABOTEUR"))
    }
}
