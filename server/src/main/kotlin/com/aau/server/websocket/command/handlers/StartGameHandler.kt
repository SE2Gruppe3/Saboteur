package com.aau.server.websocket.command.handlers

import com.aau.server.service.GameLifecycleService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.StartGameCommand
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

@Component
class StartGameHandler(
    private val messagingService: MessagingService,
    private val gameLifecycleService: GameLifecycleService
) : CommandHandler<StartGameCommand> {

    override val commandType: String = "START_GAME"
    override val commandClass: KClass<StartGameCommand> = StartGameCommand::class

    override fun handle(session: WebSocketSession, command: StartGameCommand) {
        val lobbyCode = messagingService.getLobbyCodeForSession(session.id)
            ?: throw IllegalArgumentException("Session ist mit keiner Lobby verbunden")
        
        val playerId = messagingService.getPlayerIdForSession(session.id)
            ?: throw IllegalArgumentException("Session ist mit keinem Spieler verknüpft")

        messagingService.getLobbyLock(lobbyCode).withLock {
            // Orchestration is now handled atomically by GameLifecycleService
            gameLifecycleService.startGame(lobbyCode, playerId, command.players)
        }
    }
}
