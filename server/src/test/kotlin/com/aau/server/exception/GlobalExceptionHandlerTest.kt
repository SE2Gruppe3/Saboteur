package com.aau.server.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `IllegalArgumentException returns 400 with ErrorResponse`() {
        val response = handler.handleBadRequest(IllegalArgumentException("Ungültige Eingabe"))
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("BAD_REQUEST", response.body?.code)
        assertEquals("Ungültige Eingabe", response.body?.message)
    }

    @Test
    fun `NoSuchElementException returns 404 with ErrorResponse`() {
        val response = handler.handleNotFound(NoSuchElementException("Nicht gefunden"))
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("NOT_FOUND", response.body?.code)
    }

    @Test
    fun `SecurityException returns 403 with ErrorResponse`() {
        val response = handler.handleForbidden(SecurityException("Nicht autorisiert"))
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("FORBIDDEN", response.body?.code)
    }

    @Test
    fun `generic Exception returns 500 with generic message`() {
        val response = handler.handleGeneral(RuntimeException("boom"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body?.code)
    }
}
