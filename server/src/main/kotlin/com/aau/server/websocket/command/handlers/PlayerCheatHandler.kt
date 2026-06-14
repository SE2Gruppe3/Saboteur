package com.aau.server.websocket.command.handlers

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.PlayerCheatCommand
import com.aau.server.websocket.event.GameEvent
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

@Component
class PlayerCheatHandler(
    private val messagingService: MessagingService,
    private val turnManager: TurnManager,
    private val lobbyService: LobbyService
) : CommandHandler<PlayerCheatCommand> {

    override val commandType: String = "PLAYER_CHEAT"
    override val commandClass: KClass<PlayerCheatCommand> = PlayerCheatCommand::class

    override fun handle(session: WebSocketSession, command: PlayerCheatCommand) {
        messagingService.getLobbyLock(command.lobbyCode).withLock {
            val sessionPlayerId = messagingService.getPlayerIdForSession(session.id)
                ?: throw IllegalArgumentException("Session ist mit keinem Spieler verknüpft")

            val result = turnManager.cheatPlayer(
                lobbyCode = command.lobbyCode,
                playerId = sessionPlayerId,
                cheatType = command.cheatType
            )

            messagingService.sendEventToLobby(command.lobbyCode, GameEvent.GameStateUpdate(result.updatedGameState))
            messagingService.sendEventToLobby(command.lobbyCode, GameEvent.CardsDealt(result.updatedHands))

            if (result.winner != null) {
                messagingService.sendEventToLobby(command.lobbyCode, GameEvent.GameOver(result.winner))
                if (result.updatedGameState.isGameOver) {
                    lobbyService.resetAfterGame(command.lobbyCode)
                }
            }
        }
    }
}
