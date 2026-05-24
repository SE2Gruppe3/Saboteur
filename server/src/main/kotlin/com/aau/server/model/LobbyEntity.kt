package com.aau.server.model

import com.aau.saboteur.model.LobbyVisibility
import jakarta.persistence.*

@Entity
@Table(name = "lobbies")
class LobbyEntity(
    @Id
    var lobbyCode: String = "",
    @Column(nullable = false)
    var hostId: String = "",
    @Column(nullable = false)
    var gameStarted: Boolean = false,
    @Column(columnDefinition = "CLOB")
    var playersJson: String = "", // Serialized List<Player>
    @Column(nullable = false)
    var lastActivity: Long = System.currentTimeMillis(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var visibility: LobbyVisibility = LobbyVisibility.PUBLIC
) {
    constructor() : this("", "", false, "", System.currentTimeMillis(), LobbyVisibility.PUBLIC)
}
