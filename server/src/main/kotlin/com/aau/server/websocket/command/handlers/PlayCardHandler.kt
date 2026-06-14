package com.aau.server.websocket.command.handlers

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.PlayCardCommand
import com.aau.server.websocket.event.GameEvent
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

@Component
class PlayCardHandler(
    private val messagingService: MessagingService,
    private val turnManager: TurnManager,
    private val lobbyService: LobbyService
) : CommandHandler<PlayCardCommand> {

    override val commandType: String = "PLAY_CARD"
    override val commandClass: KClass<PlayCardCommand> = PlayCardCommand::class

    override fun handle(session: WebSocketSession, command: PlayCardCommand) {
        val lobbyCode = messagingService.getLobbyCodeForSession(session.id)
            ?: throw IllegalArgumentException("Session ist mit keiner Lobby verbunden")

        // Final Fix: Sequential processing per lobby
        messagingService.getLobbyLock(lobbyCode).withLock {
            val sessionPlayerId = messagingService.getPlayerIdForSession(session.id)
                ?: throw IllegalArgumentException("Session ist mit keinem Spieler verknüpft")

            require(sessionPlayerId == command.playerId) {
                "Spieler-ID stimmt nicht überein: Session gehört $sessionPlayerId"
            }

            val result = turnManager.playCard(
                lobbyCode = lobbyCode,
                playerId = command.playerId,
                cardId = command.cardId,
                position = command.position,
                isRotated = command.isRotated
            )

            messagingService.sendEventToLobby(
                lobbyCode,
                GameEvent.GameStateUpdate(result.updatedGameState)
            )
            messagingService.sendEventToLobby(lobbyCode, GameEvent.CardsDealt(result.updatedHands))
            messagingService.sendRoleUpdates(result.newPlayerRoles)

            if (result.winner != null) {
                messagingService.sendEventToLobby(lobbyCode, GameEvent.GameOver(result.winner))
                if (result.updatedGameState.isGameOver) {
                    lobbyService.resetAfterGame(lobbyCode)
                }
            }
        }
    }
}


