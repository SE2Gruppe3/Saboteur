package com.aau.server.controller

import com.aau.saboteur.model.User
import com.aau.server.model.UserEntity
import com.aau.server.repository.UserRepository
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import java.security.MessageDigest

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = ["*"])
class AuthController(private val userRepository: UserRepository) {

    /**
     * Hilfsfunktion: Hasht einen String mit SHA-256
     */
    private fun hashPassword(password: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Lazy Login: Loggt den User ein oder registriert ihn automatisch.
     */
    @PostMapping("/login")
    fun login(@RequestBody loginData: Map<String, String>): ResponseEntity<Any> {
        val username = loginData["username"] ?: ""
        val rawPassword = loginData["password"] ?: ""

        if (username.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Benutzername darf nicht leer sein.")
        }

        val hashedPassword = hashPassword(rawPassword)
        var entity = userRepository.findByUsername(username)

        // 1. Fall: User existiert noch gar nicht -> Registrieren (Lazy)
        if (entity == null) {
            val newEntity = userRepository.save(UserEntity(username = username, passwordHash = hashedPassword))
            return ResponseEntity.ok(User(newEntity.id, newEntity.username, newEntity.passwordHash))
        }

        // 2. Fall: User existiert -> Passwort prüfen
        return if (entity.passwordHash == hashedPassword) {
            ResponseEntity.ok(User(entity.id, entity.username, entity.passwordHash))
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Dieser Nutzername ist bereits vergeben oder das Passwort ist falsch.")
        }
    }

    @PostMapping("/register")
    fun register(@RequestBody sharedUser: User): ResponseEntity<Any> {
        if (userRepository.findByUsername(sharedUser.username) != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username bereits vergeben!")
        }

        val entity = userRepository.save(UserEntity(
            username = sharedUser.username,
            passwordHash = hashPassword(sharedUser.passwordHash)
        ))

        return ResponseEntity.ok(User(entity.id, entity.username, entity.passwordHash))
    }
}