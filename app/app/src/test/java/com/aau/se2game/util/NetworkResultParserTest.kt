package com.aau.se2game.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkResultParserTest {
    @Test
    fun testFormatResult_200() {
        val result = NetworkResultParser.formatResult(200, "OK")
        assertEquals("Success: HTTP 200\nOK", result)
    }

    @Test
    fun testFormatResult_299() {
        val result = NetworkResultParser.formatResult(299, "Last Success")
        assertEquals("Success: HTTP 299\nLast Success", result)
    }

    @Test
    fun testFormatResult_300() {
        val result = NetworkResultParser.formatResult(300, "Redirect")
        assertEquals("Failed: HTTP 300\nRedirect", result)
    }

    @Test
    fun testFormatResult_404() {
        val result = NetworkResultParser.formatResult(404, "Not Found")
        assertEquals("Failed: HTTP 404\nNot Found", result)
    }

    @Test
    fun testFormatResult_199() {
        val result = NetworkResultParser.formatResult(199, "Informational")
        assertEquals("Failed: HTTP 199\nInformational", result)
    }

    @Test
    fun testFormatResult_EmptyBody() {
        val result = NetworkResultParser.formatResult(200, "")
        assertEquals("Success: HTTP 200\n", result)
    }
}
