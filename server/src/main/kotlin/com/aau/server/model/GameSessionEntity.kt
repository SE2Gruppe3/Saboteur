package com.aau.server.model

import jakarta.persistence.*

@Entity
@Table(name = "game_sessions")
class GameSessionEntity(
    @Id
    @Column(name = "session_id", nullable = false)
    var sessionId: String = "",

    @Lob
    @Column(name = "game_state_json", columnDefinition = "CLOB")
    var gameStateJson: String = "",

    @Lob
    @Column(name = "players_json", columnDefinition = "CLOB")
    var playersJson: String = "",

    @Column(name = "is_started")
    var isStarted: Boolean = false
) {
    constructor() : this("", "", "", false)
}
