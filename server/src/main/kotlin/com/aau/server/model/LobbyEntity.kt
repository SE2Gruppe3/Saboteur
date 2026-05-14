package com.aau.server.model

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
    var playersJson: String = "" // Serialized List<Player>
) {
    constructor() : this("", "", false, "")
}
