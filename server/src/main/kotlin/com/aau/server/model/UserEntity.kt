package com.aau.server.model

import jakarta.persistence.*

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
    var passwordHash: String = ""
) {
    constructor() : this(null, "", "")
}
