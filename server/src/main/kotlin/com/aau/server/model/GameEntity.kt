package com.aau.server.model

import jakarta.persistence.*

@Entity
@Table(name = "games")
class GameEntity(
    @Id
    var lobbyCode: String = "",
    
    var currentPlayerId: String? = null,
    
    @Column(columnDefinition = "CLOB")
    var boardJson: String = "", // List<PlacedTunnelCard>
    
    @Column(columnDefinition = "CLOB")
    var drawPileJson: String = "", // List<TunnelCard>
    
    @Column(columnDefinition = "CLOB")
    var discardPileJson: String = "", // List<TunnelCard>
    
    @Column(columnDefinition = "CLOB")
    var handsJson: String = "", // Map<String, List<TunnelCard>>
    
    @Column(columnDefinition = "CLOB")
    var playersTurnJson: String = "", // List<PlayerTurn>
    
    @Column(columnDefinition = "CLOB")
    var playerRolesJson: String = "", // Map<String, Player> (contains roles)
    
    var deckWasEmptied: Boolean = false,
    var passedSinceEmpty: Int = 0
) {
    constructor() : this("")
}
