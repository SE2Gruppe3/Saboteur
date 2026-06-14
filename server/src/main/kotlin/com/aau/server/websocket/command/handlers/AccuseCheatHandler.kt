package com.aau.server.websocket.command.handlers

import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.AccuseCheatCommand
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.event.GameEvent
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

@Component
class AccuseCheatHandler(
    private val messagingService: MessagingService,
    private val turnManager: TurnManager
) : CommandHandler<AccuseCheatCommand> {

    override val commandType: String = "ACCUSE_CHEAT"
    override val commandClass: KClass<AccuseCheatCommand> = AccuseCheatCommand::class

    override fun handle(session: WebSocketSession, command: AccuseCheatCommand) {
        messagingService.getLobbyLock(command.lobbyCode).withLock {
            val sessionPlayerId = messagingService.getPlayerIdForSession(session.id)
                ?: throw IllegalArgumentException("Session ist mit keinem Spieler verknüpft")

            val result = turnManager.accuseCheating(
                lobbyCode = command.lobbyCode,
                accuserPlayerId = sessionPlayerId,
                accusedPlayerId = command.accusedPlayerId
            )

            messagingService.sendEventToLobby(command.lobbyCode, GameEvent.CheatAccusationResultEvent(result))
        }
    }
}
