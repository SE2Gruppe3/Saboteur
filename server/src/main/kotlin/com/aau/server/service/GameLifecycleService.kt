package com.aau.server.service

import com.aau.saboteur.model.Player
import com.aau.server.model.GameStartResult
import com.aau.server.websocket.event.GameEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GameLifecycleService(
    private val lobbyService: LobbyService,
    private val gameService: GameService,
    private val turnManager: TurnManager,
    private val messagingService: MessagingService
) {
    private val logger = LoggerFactory.getLogger(GameLifecycleService::class.java)

    /**
     * Startet ein Spiel atomar. Garantiert, dass Lobby-Status, Rollen und 
     * GameState konsistent in der DB landen, bevor Events gesendet werden.
     */
    @Transactional
    fun startGame(lobbyCode: String, hostPlayerId: String, players: List<Player>) {
        logger.info("Starting game for lobby $lobbyCode initiated by $hostPlayerId")
        
        val lobby = lobbyService.getLobby(lobbyCode)
        require(lobby.hostId == hostPlayerId) { "Only host can start" }
        require(!lobby.gameStarted) { "Game already started" }

        // 1. Logik berechnen
        val result = gameService.startGame(players)
        
        // 2. State Mutationen (DB + RAM via Transactional Services)
        gameService.setPlayerData(lobbyCode, result.playerRoles)
        turnManager.initializeGame(lobbyCode, result.cardDistribution, result.gameState)
        val startedLobby = lobbyService.markGameStarted(lobbyCode)

        // 3. Erst NACH erfolgreichem Commit werden die Events gesendet (MessagingService)
        broadcastStartEvents(lobbyCode, startedLobby, result)
        
        logger.info("Game for lobby $lobbyCode started successfully")
    }

    private fun broadcastStartEvents(lobbyCode: String, lobby: com.aau.saboteur.model.LobbyState, result: GameStartResult) {
        // REIHENFOLGE GEÄNDERT: Erst Spieldaten, dann Lobby-Update (Navigation)
        // Das verhindert Race Conditions auf dem Client.
        
        // 1. Game State senden
        messagingService.sendEventToLobby(lobbyCode, GameEvent.GameStateUpdate(result.gameState))
        
        // 2. Rollen senden
        result.playerRoles.forEach { (pid, player) ->
            messagingService.sendEventToPlayer(pid, GameEvent.PlayerDataUpdate(player))
        }
        
        // 3. Karten senden
        messagingService.sendEventToLobby(lobbyCode, GameEvent.CardsDealt(result.cardDistribution.hands))
        
        // 4. Lobby State Update (Triggert Navigation im Client)
        messagingService.sendEventToLobby(lobbyCode, GameEvent.LobbyStateUpdate(lobby))
        
        // 5. Globale Listen-Updates
        messagingService.broadcastEvent(GameEvent.LobbyListUpdate(lobbyService.getAllLobbies()))
    }
}
