package com.aau.server.websocket.command.handlers

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.LobbyKickCommand
import com.aau.server.websocket.event.GameEvent
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

@Component
class LobbyKickHandler(
    private val messagingService: MessagingService,
    private val lobbyService: LobbyService
) : CommandHandler<LobbyKickCommand> {

    override val commandType: String = "LOBBY_KICK"
    override val commandClass: KClass<LobbyKickCommand> = LobbyKickCommand::class

    override fun handle(session: WebSocketSession, command: LobbyKickCommand) {
        messagingService.getLobbyLock(command.lobbyCode).withLock {
            val lobby = try {
                lobbyService.getLobby(command.lobbyCode)
            } catch (e: Exception) {
                messagingService.sendEventToSession(session.id, GameEvent.ErrorEvent("Lobby nicht gefunden"))
                return
            }

            if (lobby.hostId != command.hostId) {
                messagingService.sendEventToSession(session.id, GameEvent.ErrorEvent("Nur der Host darf Spieler kicken"))
                return
            }

            if (command.targetPlayerId == lobby.hostId) {
                messagingService.sendEventToSession(session.id, GameEvent.ErrorEvent("Der Host kann sich nicht selbst kicken"))
                return
            }

            try {
                lobbyService.kickPlayer(command.lobbyCode, command.targetPlayerId)
                // The kicked player needs to be notified specifically so they can leave the lobby screen
                messagingService.sendEventToPlayer(command.targetPlayerId, GameEvent.PlayerKicked(command.targetPlayerId))
            } catch (e: Exception) {
                messagingService.sendEventToSession(session.id, GameEvent.ErrorEvent(e.message ?: "Fehler beim Kicken des Spielers"))
            }
        }
    }
}
