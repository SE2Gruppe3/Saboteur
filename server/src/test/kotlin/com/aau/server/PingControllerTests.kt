package com.aau.server

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class PingControllerTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `ping returns ok status`() {
        mockMvc.perform(get("/api/ping"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.message").value("Frontend can reach the backend"))
    }

    @Test
    fun `PingResponse data class methods work`() {
        val response1 = PingResponse("ok", "msg")
        val response2 = PingResponse("ok", "msg")
        val response3 = PingResponse("error", "msg")

        assertEquals(response1, response2)
        assertNotEquals(response1, response3)
        assertEquals(response1.hashCode(), response2.hashCode())
        assertEquals("PingResponse(status=ok, message=msg)", response1.toString())
        
        val copied = response1.copy(status = "new")
        assertEquals("new", copied.status)
        assertEquals("msg", copied.message)
        
        assertEquals("ok", response1.component1())
        assertEquals("msg", response1.component2())
    }
}
