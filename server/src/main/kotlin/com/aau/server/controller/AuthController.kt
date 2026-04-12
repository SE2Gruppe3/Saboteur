package com.aau.server.controller

import com.aau.saboteur.model.User
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = ["*"]) // Erlaubt der App den Zugriff auf den Server
class AuthController {

    @PostMapping("/login")
    fun login(@RequestBody loginRequest: Map<String, String>): User {
        val username = loginRequest["username"] ?: "Unbekannter Gräber"

        return User(
            id = java.util.UUID.randomUUID().toString(),
            username = username,
            score = 0
        )
    }
}