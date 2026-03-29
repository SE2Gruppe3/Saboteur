package com.aau.se2game.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkResultParserTest {
    @Test
    fun testFormatResult_Success() {
        val result = NetworkResultParser.formatResult(200, "{\"status\":\"ok\"}")
        assertEquals("Success: HTTP 200\n{\"status\":\"ok\"}", result)
    }

    @Test
    fun testFormatResult_Failure() {
        val result = NetworkResultParser.formatResult(404, "Not Found")
        assertEquals("Failed: HTTP 404\nNot Found", result)
    }
}
