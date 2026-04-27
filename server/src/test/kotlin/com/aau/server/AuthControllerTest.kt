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
    @Test
    fun `login returns bad request for empty username`() {
        // GIVEN
        val loginData = mapOf("username" to "", "password" to "123")

        // WHEN
        val response = authController.login(loginData)

        // THEN
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Benutzername darf nicht leer sein.", response.body)
    }

    @Test
    fun `register returns bad request if user exists`() {
        // GIVEN
        val sharedUser = User(id = null, username = "exists", passwordHash = "pw")
        `when`(userRepository.findByUsername("exists")).thenReturn(UserEntity(username = "exists", passwordHash = "hash"))

        // WHEN
        val response = authController.register(sharedUser)

        // THEN
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Username bereits vergeben!", response.body)
    }

    @Test
    fun `register saves new user successfully`() {
        // GIVEN
        val sharedUser = User(id = null, username = "newGuy", passwordHash = "rawPw")
        `when`(userRepository.findByUsername("newGuy")).thenReturn(null)

        // Wir mocken das Speichern, damit keine NPE fliegt
        val savedEntity = UserEntity(id = 99L, username = "newGuy", passwordHash = "hashedPw")
        `when`(userRepository.save(any(UserEntity::class.java))).thenReturn(savedEntity)

        // WHEN
        val response = authController.register(sharedUser) as ResponseEntity<User>

        // THEN
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("newGuy", response.body?.username)
    }
    @Test
    fun `test UserEntity properties`() {
        val entity = UserEntity(username = "test", passwordHash = "hash")
        entity.id = 1L

        assertEquals("test", entity.username)
        assertEquals("hash", entity.passwordHash)
        assertEquals(1L, entity.id)
    }
}