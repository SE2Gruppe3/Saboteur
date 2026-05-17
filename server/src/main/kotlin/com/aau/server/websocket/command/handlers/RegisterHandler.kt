package com.aau.server.websocket.command.handlers

import com.aau.saboteur.model.ReconnectSnapshot
import com.aau.server.service.GameService
import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import com.aau.server.service.TurnManager
import com.aau.server.websocket.command.CommandHandler
import com.aau.server.websocket.command.RegisterCommand
import com.aau.server.websocket.event.GameEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

@Component
class RegisterHandler(
    private val messagingService: MessagingService,
    private val lobbyService: LobbyService,
    private val gameService: GameService,
    private val turnManager: TurnManager
) : CommandHandler<RegisterCommand> {
    private val logger = LoggerFactory.getLogger(RegisterHandler::class.java)

    override val commandType: String = "REGISTER"
    override val commandClass: KClass<RegisterCommand> = RegisterCommand::class

    override fun handle(session: WebSocketSession, command: RegisterCommand) {
        val playerId = command.playerId
        val lobbyCode = command.lobbyCode

        // 1. LOCK lobby to prevent parallel state changes during sync
        messagingService.getLobbyLock(lobbyCode).withLock {
            
            // 2. REGISTER/REPLACE session (starts buffering)
            messagingService.registerPlayer(session.id, playerId)
            messagingService.joinLobbyGroup(session.id, lobbyCode)

            logger.info("REGISTER: Player {} in lobby {}", playerId, lobbyCode)

            try {
                // 3. Build DETERMINISTIC SNAPSHOT from services
                val lobby = lobbyService.getLobby(lobbyCode)
                val playerInLobby = lobby.players.find { it.id == playerId }
                    ?: throw IllegalArgumentException("Player $playerId not in lobby $lobbyCode")

                val snapshot = if (lobby.gameStarted) {
                    val gameState = turnManager.getGameState(lobbyCode)
                    val basePlayer = gameService.getPlayer(lobbyCode, playerId) ?: playerInLobby
                    
                    // CRITICAL FIX: Merge role from GameService with current hand from TurnManager
                    val currentHands = turnManager.getHands(lobbyCode)
                    val playerWithHand = basePlayer.copy(
                        hand = currentHands[playerId] ?: emptyList()
                    )

                    ReconnectSnapshot(
                        lobbyState = lobby,
                        gameState = gameState,
                        playerState = playerWithHand,
                        serverTimestamp = System.currentTimeMillis()
                    )
                } else {
                    ReconnectSnapshot(
                        lobbyState = lobby,
                        gameState = null,
                        playerState = playerInLobby,
                        serverTimestamp = System.currentTimeMillis()
                    )
                }

                // 4. SEND SNAPSHOT (This skips the buffer in MessagingService.sendEventToSession)
                messagingService.sendEventToSession(session.id, GameEvent.ReconnectSnapshotEvent(snapshot))
                
                // NOTE: We do NOT send SYNC_COMPLETE here. 
                // We wait for the client to send SYNC_ACK to ensure it processed the snapshot.
                // SyncAckHandler will then flush the buffer and send SYNC_COMPLETE.

            } catch (e: Exception) {
                if (e.message?.contains("not found", ignoreCase = true) == true) {
                    logger.warn("Sync failed: Lobby {} not found for player {}", lobbyCode, playerId)
                    messagingService.sendEventToSession(session.id, GameEvent.LobbyNotFound())
                } else {
                    logger.error("Sync failed for player {}: {}", playerId, e.message)
                    messagingService.sendEventToSession(session.id, GameEvent.ErrorEvent("Sync failed: ${e.message}"))
                }
                // Recovery: ensure session isn't stuck in syncing forever
                messagingService.setSessionSynced(session.id)
            }
        }
    }
}
