package com.aau.server.websocket.command.handlers

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.DiscardCardCommand
import com.aau.server.websocket.event.GameEvent
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

@Component
class DiscardCardHandler(
    private val messagingService: MessagingService,
    private val turnManager: TurnManager,
    private val lobbyService: LobbyService
) : CommandHandler<DiscardCardCommand> {

    override val commandType: String = "DISCARD_CARD"
    override val commandClass: KClass<DiscardCardCommand> = DiscardCardCommand::class

    override fun handle(session: WebSocketSession, command: DiscardCardCommand) {
        val lobbyCode = messagingService.getLobbyCodeForSession(session.id)
            ?: throw IllegalArgumentException("Session is not connected to a lobby")
        
        messagingService.getLobbyLock(lobbyCode).withLock {
            val sessionPlayerId = messagingService.getPlayerIdForSession(session.id)
                ?: throw IllegalArgumentException("Session is not linked to a player")

            require(sessionPlayerId == command.playerId) {
                "Player ID mismatch: session belongs to $sessionPlayerId"
            }

            val result = turnManager.discardCard(
                lobbyCode = lobbyCode,
                playerId = command.playerId,
                cardId = command.cardId
            )

            messagingService.sendEventToLobby(lobbyCode, GameEvent.GameStateUpdate(result.updatedGameState))
            messagingService.sendEventToLobby(lobbyCode, GameEvent.CardsDealt(result.updatedHands))
            
            if (result.winner != null) {
                messagingService.sendEventToLobby(lobbyCode, GameEvent.GameOver(result.winner))
                // Clean up lobby after game over so players aren't "pulled back" into a finished game
                lobbyService.deleteLobbyInternal(lobbyCode, "game_over")
            }
        }
    }
}
