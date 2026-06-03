package com.aau.server.websocket.command.handlers

import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.PlayRockfallCardCommand
import com.aau.server.websocket.event.GameEvent
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

@Component
class PlayRockfallCardHandler(
    private val messagingService: MessagingService,
    private val turnManager: TurnManager
) : CommandHandler<PlayRockfallCardCommand> {

    override val commandType: String = "PLAY_ROCKFALL_CARD"
    override val commandClass: KClass<PlayRockfallCardCommand> = PlayRockfallCardCommand::class

    override fun handle(session: WebSocketSession, command: PlayRockfallCardCommand) {
        val lobbyCode = messagingService.getLobbyCodeForSession(session.id)
            ?: throw IllegalArgumentException("Session ist mit keiner Lobby verbunden")
        
        messagingService.getLobbyLock(lobbyCode).withLock {
            val sessionPlayerId = messagingService.getPlayerIdForSession(session.id)
                ?: throw IllegalArgumentException("Session ist mit keinem Spieler verknüpft")

            require(sessionPlayerId == command.playerId) { "Spieler-ID stimmt nicht überein" }

            val result = turnManager.playRockfallCard(
                lobbyCode = lobbyCode,
                playerId = command.playerId,
                cardId = command.cardId,
                targetPosition = command.targetPosition
            )

            messagingService.sendEventToLobby(lobbyCode, GameEvent.GameStateUpdate(result.updatedGameState))
            messagingService.sendEventToLobby(lobbyCode, GameEvent.CardsDealt(result.updatedHands))
        }
    }
}
