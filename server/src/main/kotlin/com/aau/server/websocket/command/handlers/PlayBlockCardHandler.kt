package com.aau.server.websocket.command.handlers

import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.PlayBlockCardCommand
import com.aau.server.websocket.event.GameEvent
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

@Component
class PlayBlockCardHandler(
    private val messagingService: MessagingService,
    private val turnManager: TurnManager
) : CommandHandler<PlayBlockCardCommand> {

    override val commandType: String = "PLAY_BLOCK_CARD"
    override val commandClass: KClass<PlayBlockCardCommand> = PlayBlockCardCommand::class

    override fun handle(session: WebSocketSession, command: PlayBlockCardCommand) {
        val lobbyCode = messagingService.getLobbyCodeForSession(session.id)
            ?: throw IllegalArgumentException("Session ist mit keiner Lobby verbunden")
        
        messagingService.getLobbyLock(lobbyCode).withLock {
            val sessionPlayerId = messagingService.getPlayerIdForSession(session.id)
                ?: throw IllegalArgumentException("Session ist mit keinem Spieler verknüpft")

            require(sessionPlayerId == command.playerId) {
                "Spieler-ID stimmt nicht überein"
            }

            val result = turnManager.playBlockCard(
                lobbyCode = lobbyCode,
                playerId = command.playerId,
                cardId = command.cardId,
                targetPlayerId = command.targetPlayerId
            )

            messagingService.sendEventToLobby(lobbyCode, GameEvent.GameStateUpdate(result.updatedGameState))
            messagingService.sendEventToLobby(lobbyCode, GameEvent.CardsDealt(result.updatedHands))
            messagingService.sendRoleUpdates(result.newPlayerRoles)
        }
    }
}
