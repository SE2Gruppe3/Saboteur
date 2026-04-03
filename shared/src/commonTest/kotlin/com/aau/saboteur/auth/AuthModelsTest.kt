package com.aau.saboteur.auth

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class AuthModelsTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testLoginRequestGuestSerialization() {

        val request = LoginRequest(username = "Dwarf1", isGuest = true)
        val result = json.encodeToString(request)


        assertContains(result, "\"passwordHash\":null")
        assertContains(result, "\"isGuest\":true")
    }

    @Test
    fun testAuthResponseMapping() {

        val serverJson = """{"success":true,"token":"abc-123"}"""
        val response = Json.decodeFromString<AuthResponse>(serverJson)

        assertEquals(true, response.success)
        assertEquals("abc-123", response.token)
        assertEquals(null, response.errorMessage)
    }
}