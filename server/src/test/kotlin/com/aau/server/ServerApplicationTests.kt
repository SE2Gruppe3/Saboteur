package com.aau.server

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ServerApplicationTests {

    @Test
    fun contextLoads() {
        // Simple test to ensure application context starts
    }

    @Test
    fun `main method runs without error`() {
        // Calling main with empty args to cover the entry point
        main(arrayOf())
    }
}
