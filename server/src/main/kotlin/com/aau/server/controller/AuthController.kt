package com.aau.server.controller

import com.aau.saboteur.model.User
import com.aau.server.model.UserEntity
import com.aau.server.repository.UserRepository
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = ["*"])
class AuthController(private val userRepository: UserRepository) {

    /**
     * Lazy Login: Loggt den User ein oder registriert ihn automatisch,
     * falls er noch nicht existiert.
     */
    @PostMapping("/login")
    fun login(@RequestBody loginData: Map<String, String>): ResponseEntity<Any> {
        val username = loginData["username"] ?: ""
        val password = loginData["password"] ?: ""

        if (username.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Benutzername darf nicht leer sein.")
        }

        var entity = userRepository.findByUsername(username)

        // 1. Fall: User existiert noch gar nicht -> Registrieren (Lazy)
        if (entity == null) {
            val newEntity = userRepository.save(UserEntity(username = username, passwordHash = password))
            return ResponseEntity.ok(User(newEntity.id, newEntity.username, newEntity.passwordHash))
        }

        // 2. Fall: User existiert -> Passwort prüfen
        return if (entity.passwordHash == password) {
            // Passwort korrekt
            ResponseEntity.ok(User(entity.id, entity.username, entity.passwordHash))
        } else {
            // Passwort falsch
            // Da der User existiert, das PW aber nicht passt, geben wir eine klare Meldung
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Dieser Nutzername ist bereits vergeben oder das Passwort ist falsch.")
        }
    }

    // Die separate Register-Funktion lassen wir als Backup drin
    @PostMapping("/register")
    fun register(@RequestBody sharedUser: User): ResponseEntity<Any> {
        if (userRepository.findByUsername(sharedUser.username) != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username bereits vergeben!")
        }
        val entity = userRepository.save(UserEntity(
            username = sharedUser.username,
            passwordHash = sharedUser.passwordHash
        ))
        return ResponseEntity.ok(User(entity.id, entity.username, entity.passwordHash))
    }
}