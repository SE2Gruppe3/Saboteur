package com.aau.saboteur.model

import kotlinx.serialization.Serializable

/**
 * ACHTUNG: Dies ist das Shared-Modell für den Datentransfer (DTO).
 * Für Datenbank-Operationen im Server siehe: com.aau.saboteur.server.model.UserEntity
 */
@Serializable
data class User(
    var id: Long? = null,
    var username: String = "",
    var passwordHash: String = ""
)