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

    @Test
    fun testRegisterRequestStructure() {
        val regRequest = RegisterRequest(username = "NewDwarf", passwordHash = "hashed_pw_123")

        assertEquals("NewDwarf", regRequest.username)
        assertEquals("hashed_pw_123", regRequest.passwordHash)
    }
}