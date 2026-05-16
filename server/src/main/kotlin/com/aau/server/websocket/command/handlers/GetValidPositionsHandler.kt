package com.aau.server.websocket.command.handlers

import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.GetValidPositionsCommand
import com.aau.server.websocket.event.GameEvent
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.reflect.KClass

@Component
class GetValidPositionsHandler(
    private val messagingService: MessagingService,
    private val turnManager: TurnManager
) : CommandHandler<GetValidPositionsCommand> {

    override val commandType: String = "GET_VALID_POSITIONS"
    override val commandClass: KClass<GetValidPositionsCommand> = GetValidPositionsCommand::class

    override fun handle(session: WebSocketSession, command: GetValidPositionsCommand) {
        val lobbyCode = messagingService.getLobbyCodeForSession(session.id)
            ?: throw IllegalArgumentException("Session is not connected to a lobby")
        
        val sessionPlayerId = messagingService.getPlayerIdForSession(session.id)
            ?: throw IllegalArgumentException("Session is not linked to a player")

        val hands = turnManager.getHands(lobbyCode)
        val card = hands[sessionPlayerId]?.find { it.id == command.cardId }
            ?: throw IllegalArgumentException("Card ${command.cardId} not found in hand")

        val placements = turnManager.getGameStateSnapshot(lobbyCode).boardPlacements
        val validPositions = turnManager.getValidPositions(lobbyCode, card, command.isRotated, placements)

        messagingService.sendEventToSession(session.id, GameEvent.ValidPositions(validPositions))
    }
}
