package com.aau.server

import com.aau.shared.game.CreateGameRequest
import com.aau.shared.game.Player
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class GameControllerTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `getGameState returns current state`() {
        mockMvc.perform(get("/api/game"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.players").isArray)
    }

    @Test
    fun `startGame returns randomized turn order`() {
        val request = CreateGameRequest(
            players = listOf(
                Player("1", "Alice"),
                Player("2", "Bob")
            )
        )

        mockMvc.perform(
            post("/api/game/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.players").isArray)
            .andExpect(jsonPath("$.currentPlayerId").exists())
    }
}
