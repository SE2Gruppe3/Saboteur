package com.aau.server

import com.aau.saboteur.model.User
import com.aau.server.controller.AuthController
import com.aau.server.model.UserEntity
import com.aau.server.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        assertEquals(hashedPassword, response.body?.passwordHash)
    }

    @Test
    fun `guest login creates new entity with isGuest true`() {
        // GIVEN
        val loginData = mapOf("username" to "GuestMax", "password" to "")
        `when`(userRepository.findByUsername("GuestMax")).thenReturn(null)
        val savedEntity = UserEntity(id = 10L, username = "GuestMax", passwordHash = "", isGuest = true)
        `when`(userRepository.save(any(UserEntity::class.java))).thenReturn(savedEntity)

        // WHEN
        val response = authController.login(loginData) as ResponseEntity<User>

        // THEN
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("GuestMax", response.body?.username)
        val captor = ArgumentCaptor.forClass(UserEntity::class.java)
        verify(userRepository).save(captor.capture())
        assertTrue(captor.value.isGuest)
        assertEquals("", captor.value.passwordHash)
    }

    @Test
    fun `guest login returns conflict if name belongs to registered account`() {
        // GIVEN
        val loginData = mapOf("username" to "GuestMax", "password" to "")
        val registeredUser = UserEntity(id = 5L, username = "GuestMax", passwordHash = hashPassword("secret"), isGuest = false)
        `when`(userRepository.findByUsername("GuestMax")).thenReturn(registeredUser)

        // WHEN
        val response = authController.login(loginData)

        // THEN
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        verify(userRepository, never()).save(any(UserEntity::class.java))
    }

    @Test
    fun `guest login succeeds and replaces new-style orphaned ghost entity`() {
        // Regression: Gast schließt App ohne Lobby zu betreten → Ghost-Entity (isGuest=true) blockierte Wiedereintritt
        val loginData = mapOf("username" to "Max", "password" to "")
        val ghostEntity = UserEntity(id = 1L, username = "Max", passwordHash = "", isGuest = true)
        `when`(userRepository.findByUsername("Max")).thenReturn(ghostEntity)
        val freshEntity = UserEntity(id = 2L, username = "Max", passwordHash = "", isGuest = true)
        `when`(userRepository.save(any(UserEntity::class.java))).thenReturn(freshEntity)

        val response = authController.login(loginData)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(userRepository).delete(ghostEntity)
        verify(userRepository).save(any(UserEntity::class.java))
    }

    @Test
    fun `guest login succeeds and replaces legacy ghost entity from main branch`() {
        // Regression: Auf main wurden Gäste mit isGuest=false und passwordHash=SHA256("") gespeichert.
        // Persistente H2-DB enthält diese Alteinträge und blockierte Gast-Login mit 409.
        val loginData = mapOf("username" to "Max", "password" to "")
        val legacyHash = hashPassword("")
        val legacyGhost = UserEntity(id = 1L, username = "Max", passwordHash = legacyHash, isGuest = false)
        `when`(userRepository.findByUsername("Max")).thenReturn(legacyGhost)
        val freshEntity = UserEntity(id = 2L, username = "Max", passwordHash = "", isGuest = true)
        `when`(userRepository.save(any(UserEntity::class.java))).thenReturn(freshEntity)

        val response = authController.login(loginData)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(userRepository).delete(legacyGhost)
        verify(userRepository).save(any(UserEntity::class.java))
    }

    @Test
    fun `regular login succeeds and replaces new-style ghost guest entity with same name`() {
        // Regression: Ghost-Gast-Entity (isGuest=true) mit leerem passwordHash ließ regulären Login mit 401 scheitern
        val loginData = mapOf("username" to "alice", "password" to "geheim")
        val ghostGuest = UserEntity(id = 3L, username = "alice", passwordHash = "", isGuest = true)
        `when`(userRepository.findByUsername("alice")).thenReturn(ghostGuest)
        val newEntity = UserEntity(id = 4L, username = "alice", passwordHash = hashPassword("geheim"), isGuest = false)
        `when`(userRepository.save(any(UserEntity::class.java))).thenReturn(newEntity)

        val response = authController.login(loginData) as ResponseEntity<User>

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(userRepository).delete(ghostGuest)
        verify(userRepository).save(any(UserEntity::class.java))
    }

    @Test
    fun `regular login succeeds and replaces legacy ghost entity from main branch`() {
        // Regression: Alteinträge aus main (isGuest=false, passwordHash=SHA256("")) blockierten regulären Login mit 401
        val loginData = mapOf("username" to "alice", "password" to "geheim")
        val legacyGhost = UserEntity(id = 3L, username = "alice", passwordHash = hashPassword(""), isGuest = false)
        `when`(userRepository.findByUsername("alice")).thenReturn(legacyGhost)
        val newEntity = UserEntity(id = 4L, username = "alice", passwordHash = hashPassword("geheim"), isGuest = false)
        `when`(userRepository.save(any(UserEntity::class.java))).thenReturn(newEntity)

        val response = authController.login(loginData) as ResponseEntity<User>

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(userRepository).delete(legacyGhost)
        verify(userRepository).save(any(UserEntity::class.java))
    }

    @Test
    fun `login performs lazy registration if user does not exist`() {
        // GIVEN
        val username = "newGuy"
        val password = "password123"
        val hashedPassword = hashPassword(password)
        val loginData = mapOf("username" to username, "password" to password)

        `when`(userRepository.findByUsername(username)).thenReturn(null)
        val savedEntity = UserEntity(id = 100L, username = username, passwordHash = hashedPassword)
        `when`(userRepository.save(any(UserEntity::class.java))).thenReturn(savedEntity)

        // WHEN
        val response = authController.login(loginData) as ResponseEntity<User>

        // THEN
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(username, response.body?.username)
        assertEquals(100L, response.body?.id)
        verify(userRepository).save(any(UserEntity::class.java))
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
