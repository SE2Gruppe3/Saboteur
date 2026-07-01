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

    private fun hashPassword(password: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    // Erkennt Ghost-Gast-Entities unabhängig vom Schema-Stand:
    // - Neue Gäste (fix branch): isGuest=true, passwordHash=""
    // - Legacy-Gäste (main, vor isGuest-Flag): isGuest=false, passwordHash=SHA256("")
    private fun isGhostGuest(entity: UserEntity): Boolean =
        entity.isGuest || entity.passwordHash == hashPassword("")

    @PostMapping("/login")
    fun login(@RequestBody loginData: Map<String, String>): ResponseEntity<Any> {
        val username = loginData["username"] ?: ""
        val rawPassword = loginData["password"] ?: ""
        val isGuest = rawPassword.isBlank()

        if (username.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Benutzername darf nicht leer sein.")
        }

        val existingEntity = userRepository.findByUsername(username)

        if (isGuest) {
            if (existingEntity != null && !isGhostGuest(existingEntity)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Dieser Name gehört einem registrierten Account. / This name belongs to a registered account.")
            }
            existingEntity?.let { userRepository.delete(it) }
            val newEntity = userRepository.save(UserEntity(username = username, passwordHash = "", isGuest = true))
            return ResponseEntity.ok(User(newEntity.id, newEntity.username, newEntity.passwordHash, newEntity.playerId))
        }

        val hashedPassword = hashPassword(rawPassword)

        if (existingEntity == null) {
            val newEntity = userRepository.save(UserEntity(username = username, passwordHash = hashedPassword, isGuest = false))
            return ResponseEntity.ok(User(newEntity.id, newEntity.username, newEntity.passwordHash, newEntity.playerId))
        }

        if (isGhostGuest(existingEntity)) {
            userRepository.delete(existingEntity)
            val newEntity = userRepository.save(UserEntity(username = username, passwordHash = hashedPassword, isGuest = false))
            return ResponseEntity.ok(User(newEntity.id, newEntity.username, newEntity.passwordHash, newEntity.playerId))
        }

        return if (existingEntity.passwordHash == hashedPassword) {
            ResponseEntity.ok(User(existingEntity.id, existingEntity.username, existingEntity.passwordHash, existingEntity.playerId))
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

        return ResponseEntity.ok(User(entity.id, entity.username, entity.passwordHash, entity.playerId))
    }
}
