package com.aau.server

import com.aau.saboteur.model.LobbyCreateRequest
import com.aau.saboteur.model.LobbyJoinRequest
import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
import com.aau.server.controller.LobbyController
import com.aau.server.service.LobbyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(LobbyController::class)
class LobbyControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    @MockBean lateinit var lobbyService: LobbyService

    @Test
    fun `POST api lobby create returns lobby state`() {
        val state = LobbyState(
            lobbyCode = "1234",
            hostId = "1",
            players = listOf(
                Player(id = "1", name = "Host")
            ),
            gameStarted = false
        )

        `when`(lobbyService.createLobby("Host")).thenReturn(state)

        mockMvc.post("/api/lobby/create") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(LobbyCreateRequest(playerName = "Host"))
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.lobbyCode") { value("1234") }
                jsonPath("$.hostId") { value("1") }
                jsonPath("$.players.length()") { value(1) }
                jsonPath("$.players[0].name") { value("Host") }
            }
    }

    @Test
    fun `POST api lobby join returns lobby state`() {
        val state = LobbyState(
            lobbyCode = "1234",
            hostId = "1",
            players = listOf(
                Player(id = "1", name = "Host"),
                Player(id = "2", name = "Max")
            ),
            gameStarted = false
        )

        `when`(lobbyService.joinLobby("1234", "Max")).thenReturn(state)

        mockMvc.post("/api/lobby/join") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LobbyJoinRequest(lobbyCode = "1234", playerName = "Max")
            )
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.lobbyCode") { value("1234") }
                jsonPath("$.players.length()") { value(2) }
            }
    }

    @Test
    fun `POST api lobby join with illegal argument returns 400`() {
        `when`(lobbyService.joinLobby("xxxx", "Max"))
            .thenThrow(IllegalArgumentException("bad request"))

        mockMvc.post("/api/lobby/join") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LobbyJoinRequest(lobbyCode = "xxxx", playerName = "Max")
            )
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("bad request") }
            }
    }

    @Test
    fun `POST api lobby join with illegal state returns 409`() {
        `when`(lobbyService.joinLobby("1234", "Max"))
            .thenThrow(IllegalStateException("conflict"))

        mockMvc.post("/api/lobby/join") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LobbyJoinRequest(lobbyCode = "1234", playerName = "Max")
            )
        }
            .andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("conflict") }
            }
    }

    @Test
    fun `GET api lobby code returns lobby state`() {
        val state = LobbyState(
            lobbyCode = "1234",
            hostId = "1",
            players = emptyList(),
            gameStarted = false
        )

        `when`(lobbyService.getLobby("1234")).thenReturn(state)

        mockMvc.get("/api/lobby/1234")
            .andExpect {
                status { isOk() }
                jsonPath("$.lobbyCode") { value("1234") }
            }
    }
}