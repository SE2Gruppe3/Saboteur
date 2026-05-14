package com.aau.server.websocket.command.handlers

import com.aau.server.service.GameService
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.RegisterCommand
import com.aau.server.websocket.event.GameEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.reflect.KClass

@Component
class RegisterHandler(
    private val messagingService: MessagingService,
    private val lobbyService: LobbyService,
    private val gameService: GameService,
    private val turnManager: TurnManager
) : CommandHandler<RegisterCommand> {
    private val logger = LoggerFactory.getLogger(RegisterHandler::class.java)

    override val commandType: String = "REGISTER"
    override val commandClass: KClass<RegisterCommand> = RegisterCommand::class

    override fun handle(session: WebSocketSession, command: RegisterCommand) {
        messagingService.registerPlayer(session.id, command.playerId)
        messagingService.joinLobbyGroup(session.id, command.lobbyCode)
        logger.info("Player {} registered to lobby {} with session {}", command.playerId, command.lobbyCode, session.id)

        // Initial state sync after registration/reconnect
        val lobby = lobbyService.getLobby(command.lobbyCode)
        messagingService.sendEventToSession(session.id, GameEvent.LobbyStateUpdate(lobby))
        
        if (lobby.gameStarted) {
            val gameState = turnManager.getGameState(command.lobbyCode)
            messagingService.sendEventToSession(session.id, GameEvent.GameStateUpdate(gameState))
            
            gameService.getPlayer(command.lobbyCode, command.playerId)?.let {
                messagingService.sendEventToSession(session.id, GameEvent.PlayerDataUpdate(it))
            }
            
            val hand = turnManager.getHands(command.lobbyCode)[command.playerId] ?: emptyList()
            messagingService.sendEventToSession(session.id, GameEvent.CardsDealt(mapOf(command.playerId to hand)))
        }
    }
}
