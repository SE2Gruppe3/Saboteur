package com.aau.server.websocket.command.handlers

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.LobbyListFetchCommand
import com.aau.server.websocket.event.GameEvent
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.reflect.KClass

@Component
class LobbyListFetchHandler(
    private val messagingService: MessagingService,
    private val lobbyService: LobbyService
) : CommandHandler<LobbyListFetchCommand> {

    override val commandType: String = "LOBBY_LIST_FETCH"
    override val commandClass: KClass<LobbyListFetchCommand> = LobbyListFetchCommand::class

    override fun handle(session: WebSocketSession, command: LobbyListFetchCommand) {
        messagingService.sendEventToSession(session.id, GameEvent.LobbyListUpdate(lobbyService.getAllLobbies()))
    }
}
