package com.aau.saboteur.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class AuthModelsTest {

    @Test
    fun testLoginRequestForGuest() {
        val guestRequest = LoginRequest(username = "GuestUser", isGuest = true)

        assertEquals("GuestUser", guestRequest.username)
        assertTrue(guestRequest.isGuest)
        assertNull(guestRequest.passwordHash)
    }

    @Test
    fun testAuthResponseSuccess() {
        val response = AuthResponse(success = true, token = "secret-token")

        assertTrue(response.success)
        assertEquals("secret-token", response.token)
    }
}