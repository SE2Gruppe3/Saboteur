package com.aau.server.model

import jakarta.persistence.*
import java.util.UUID

/**
 * Datenbank-Entität für H2.
 * Entspricht dem Shared User-Modell (com.aau.saboteur.model.User).
 */
@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(unique = true, nullable = false)
    var username: String = "",

    @Column(nullable = false)
    var passwordHash: String = "",

    @Column(unique = true, nullable = false)
    var playerId: String = UUID.randomUUID().toString()
) {
    constructor() : this(null, "", "", UUID.randomUUID().toString())
}
