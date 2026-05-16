package com.aau.server.websocket.command.handlers

import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.PlayRepairCardCommand
import com.aau.server.websocket.event.GameEvent
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

@Component
class PlayRepairCardHandler(
    private val messagingService: MessagingService,
    private val turnManager: TurnManager
) : CommandHandler<PlayRepairCardCommand> {

    override val commandType: String = "PLAY_REPAIR_CARD"
    override val commandClass: KClass<PlayRepairCardCommand> = PlayRepairCardCommand::class

    override fun handle(session: WebSocketSession, command: PlayRepairCardCommand) {
        val lobbyCode = messagingService.getLobbyCodeForSession(session.id)
            ?: throw IllegalArgumentException("Session is not connected to a lobby")
        
        messagingService.getLobbyLock(lobbyCode).withLock {
            val sessionPlayerId = messagingService.getPlayerIdForSession(session.id)
                ?: throw IllegalArgumentException("Session is not linked to a player")

            require(sessionPlayerId == command.playerId) { "Player ID mismatch" }

            val result = turnManager.playRepairCard(
                lobbyCode = lobbyCode,
                playerId = command.playerId,
                cardId = command.cardId,
                targetPlayerId = command.targetPlayerId,
                tool = command.tool
            )

            messagingService.sendEventToLobby(lobbyCode, GameEvent.GameStateUpdate(result.updatedGameState))
            messagingService.sendEventToLobby(lobbyCode, GameEvent.CardsDealt(result.updatedHands))
        }
    }
}
