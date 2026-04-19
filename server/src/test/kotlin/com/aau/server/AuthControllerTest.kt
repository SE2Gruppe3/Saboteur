package com.aau.server.controller

import com.aau.saboteur.model.User
import com.aau.server.model.UserEntity
import com.aau.server.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthControllerTest {

    private lateinit var userRepository: UserRepository
    private lateinit var authController: AuthController

    @BeforeEach
    fun setup() {
        userRepository = mock(UserRepository::class.java)
        authController = AuthController(userRepository)
    }

    private fun hashPassword(password: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `login returns a valid user object`() {
        // GIVEN
        val username = "testUser"
        val password = "password123"
        val hashedPassword = hashPassword(password)
        val loginData = mapOf("username" to username, "password" to password)

        val existingEntity = UserEntity(id = 1L, username = username, passwordHash = hashedPassword)
        `when`(userRepository.findByUsername(username)).thenReturn(existingEntity)

        // WHEN
        val response = authController.login(loginData) as ResponseEntity<User>

        // THEN
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(username, response.body?.username)
        // Wir prüfen, ob der Server den korrekten Hash zurückgibt
        assertEquals(hashedPassword, response.body?.passwordHash)
    }

    @Test
    fun `login returns unauthorized for wrong password`() {
        // GIVEN
        val username = "testUser"
        val loginData = mapOf("username" to username, "password" to "wrongPassword")

        val existingEntity = UserEntity(id = 1L, username = username, passwordHash = hashPassword("correctPassword"))
        `when`(userRepository.findByUsername(username)).thenReturn(existingEntity)

        // WHEN
        val response = authController.login(loginData)

        // THEN
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("Dieser Nutzername ist bereits vergeben oder das Passwort ist falsch.", response.body)
    }
}