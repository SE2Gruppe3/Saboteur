package com.aau.server.health

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest(
    properties = [
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.show-components=always",
        "management.endpoint.health.probes.enabled=true"
    ]
)
@AutoConfigureMockMvc
class ActuatorHealthIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `health endpoint returns status UP and includes gameSystem component`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.gameSystem").exists())
    }

    @Test
    fun `liveness probe returns UP`() {
        // Bei aktivierten Probes ist der Pfad /actuator/health/liveness
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `readiness probe returns UP`() {
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `gameSystem component returns detailed information`() {
        // Pfad für einzelne Komponente: /actuator/health/{name}
        mockMvc.perform(get("/actuator/health/gameSystem"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.details.activeLobbies").isNumber)
    }
}
