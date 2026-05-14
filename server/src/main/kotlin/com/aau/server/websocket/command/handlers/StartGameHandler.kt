package com.aau.server.websocket.command.handlers

import com.aau.server.service.GameService
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.StartGameCommand
import com.aau.server.websocket.event.GameEvent
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.reflect.KClass

@Component
class StartGameHandler(
    private val messagingService: MessagingService,
    private val lobbyService: LobbyService,
    private val gameService: GameService,
    private val turnManager: TurnManager
) : CommandHandler<StartGameCommand> {

    override val commandType: String = "START_GAME"
    override val commandClass: KClass<StartGameCommand> = StartGameCommand::class

    override fun handle(session: WebSocketSession, command: StartGameCommand) {
        val lobbyCode = messagingService.getLobbyCodeForSession(session.id)
            ?: throw IllegalArgumentException("Session is not connected to a lobby")
        
        val lobbyState = lobbyService.getLobby(lobbyCode)
        val playerId = messagingService.getPlayerIdForSession(session.id)
            ?: throw IllegalArgumentException("Session is not linked to a player")

        require(lobbyState.hostId == playerId) {
            "Only the host can start the game"
        }

        val result = gameService.startGame(command.players)
        gameService.setPlayerData(lobbyCode, result.playerRoles)
        turnManager.initializeGame(lobbyCode, result.cardDistribution, result.gameState)

        val startedLobby = lobbyService.markGameStarted(lobbyCode)

        // Event Broadcasts using typisiertes Event-System
        messagingService.sendEventToLobby(lobbyCode, GameEvent.LobbyStateUpdate(startedLobby))
        messagingService.sendEventToLobby(lobbyCode, GameEvent.GameStateUpdate(result.gameState))
        
        result.playerRoles.forEach { (targetPlayerId, player) ->
            messagingService.sendEventToPlayer(targetPlayerId, GameEvent.PlayerDataUpdate(player))
        }
        
        messagingService.sendEventToLobby(lobbyCode, GameEvent.CardsDealt(result.cardDistribution.hands))
        messagingService.broadcastEvent(GameEvent.LobbyListUpdate(lobbyService.getAllLobbies()))
    }
}
