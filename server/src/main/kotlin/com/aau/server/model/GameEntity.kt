package com.aau.server.model

import jakarta.persistence.*

@Entity
@Table(name = "games")
class GameEntity(
    @Id
    var lobbyCode: String = "",

    var currentPlayerId: String? = null,

    @Column(columnDefinition = "CLOB")
    var boardJson: String = "",

    @Column(columnDefinition = "CLOB")
    var drawPileJson: String = "",

    @Column(columnDefinition = "CLOB")
    var discardPileJson: String = "",

    @Column(columnDefinition = "CLOB")
    var handsJson: String = "",

    @Column(columnDefinition = "CLOB")
    var playersTurnJson: String = "",

    @Column(columnDefinition = "CLOB")
    var playerRolesJson: String = "",

    var deckWasEmptied: Boolean = false,
    var passedSinceEmpty: Int = 0,

    @Column(columnDefinition = "CLOB")
    var knownGoalsByPlayerJson: String = ""
) {
    constructor() : this("")
}