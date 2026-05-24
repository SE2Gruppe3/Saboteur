package com.aau.server.service

import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.LobbyVisibility
import com.aau.saboteur.model.Player
import com.aau.server.model.LobbyEntity
import com.aau.server.repository.GameRepository
import com.aau.server.repository.LobbyRepository
import com.aau.server.websocket.event.GameEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.withLock
import kotlin.random.Random

private const val LOBBY_NOT_FOUND = "Lobby nicht gefunden"

@Service
class LobbyService(
    private val lobbyRepository: LobbyRepository,
    private val gameRepository: GameRepository,
    private val objectMapper: ObjectMapper,
    private val gameService: GameService,
    @param:Lazy private val messagingService: MessagingService,
    @param:Lazy private val turnManager: TurnManager
) {
    private val logger = LoggerFactory.getLogger(LobbyService::class.java)
    private val lobbies = ConcurrentHashMap<String, LobbyState>()
    private val lastActivity = ConcurrentHashMap<String, Long>()

    @Transactional
    fun loadFromDb(): Int {
        val all = lobbyRepository.findAll()
        all.forEach { entity ->
            try {
                val players: List<Player> = objectMapper.readValue(entity.playersJson)
                lobbies[entity.lobbyCode] = LobbyState(
                    lobbyCode = entity.lobbyCode,
                    hostId = entity.hostId,
                    players = players,
                    gameStarted = entity.gameStarted,
                    visibility = entity.visibility
                )
                lastActivity[entity.lobbyCode] = entity.lastActivity
            } catch (e: Exception) {
                logger.error("Failed to load lobby {}: {}", entity.lobbyCode, e.message)
            }
        }
        return lobbies.size
    }

    private fun persist(lobby: LobbyState) {
        val now = System.currentTimeMillis()
        lastActivity[lobby.lobbyCode] = now
        val entity = LobbyEntity(
            lobbyCode = lobby.lobbyCode,
            hostId = lobby.hostId,
            gameStarted = lobby.gameStarted,
            playersJson = objectMapper.writeValueAsString(lobby.players),
            lastActivity = now,
            visibility = lobby.visibility
        )
        lobbyRepository.save(entity)

        messagingService.sendEventToLobby(lobby.lobbyCode, GameEvent.LobbyStateUpdate(lobby))
        messagingService.broadcastEvent(GameEvent.LobbyListUpdate(getAllPublicLobbies()))
    }

    fun getActiveLobbiesCount(): Int = lobbies.size

    @Transactional
    fun createLobby(playerName: String, playerId: String? = null, visibility: LobbyVisibility = LobbyVisibility.PUBLIC): LobbyState {
        val code = generateUniqueCode()
        val finalPlayerId = playerId ?: UUID.randomUUID().toString()
        val host = Player(id = finalPlayerId, name = playerName)
        val lobby = LobbyState(code, host.id, listOf(host), false, visibility)

        messagingService.getLobbyLock(code).withLock {
            lobbies[code] = lobby
            persist(lobby)
        }
        return lobby
    }

    @Transactional
    fun joinLobby(lobbyCode: String, playerName: String, playerId: String? = null): LobbyState {
        return messagingService.getLobbyLock(lobbyCode).withLock {
            val lobby = lobbies[lobbyCode] ?: throw IllegalArgumentException(LOBBY_NOT_FOUND)

            if (playerId != null && lobby.players.any { it.id == playerId }) {
                return@withLock lobby
            }

            require(!lobby.gameStarted) { "Spiel bereits gestartet" }
            require(lobby.players.size < 10) { "Lobby ist voll" }

            val finalPlayerId = playerId ?: UUID.randomUUID().toString()
            val updatedLobby = lobby.copy(players = lobby.players + Player(finalPlayerId, playerName))
            lobbies[lobbyCode] = updatedLobby
            persist(updatedLobby)
            updatedLobby
        }
    }

    @Transactional
    fun leaveLobby(lobbyCode: String, playerId: String): LobbyState? {
        return messagingService.getLobbyLock(lobbyCode).withLock {
            val lobby = lobbies[lobbyCode] ?: throw IllegalArgumentException(LOBBY_NOT_FOUND)
            val updatedPlayers = lobby.players.filter { it.id != playerId }

            if (updatedPlayers.isEmpty()) {
                deleteLobbyInternal(lobbyCode, "empty")
                return@withLock null
            }

            val newHostId = if (lobby.hostId == playerId) updatedPlayers.first().id else lobby.hostId
            val updatedLobby = lobby.copy(players = updatedPlayers, hostId = newHostId)
            lobbies[lobbyCode] = updatedLobby
            persist(updatedLobby)
            updatedLobby
        }
    }

    @Transactional
    fun markGameStarted(lobbyCode: String): LobbyState {
        return messagingService.getLobbyLock(lobbyCode).withLock {
            val lobby = lobbies[lobbyCode] ?: throw IllegalArgumentException(LOBBY_NOT_FOUND)
            val updatedLobby = lobby.copy(gameStarted = true)
            lobbies[lobbyCode] = updatedLobby
            persist(updatedLobby)
            updatedLobby
        }
    }

    @Transactional
    fun deleteLobbyInternal(code: String, reason: String) {
        logger.info("Cleaning up {} lobby: {}", reason, code)

        try {
            messagingService.sendEventToLobby(code, GameEvent.LobbyLeft())
        } catch (e: Exception) { }

        lobbies.remove(code)
        lastActivity.remove(code)
        lobbyRepository.deleteById(code)
        gameRepository.deleteById(code)
        turnManager.removeGame(code)
        gameService.removePlayerData(code)
        messagingService.clearLobbyMappings(code)
        messagingService.broadcastEvent(GameEvent.LobbyListUpdate(getAllPublicLobbies()))
    }

    fun getAllPublicLobbies(): List<LobbyState> = lobbies.values.filter { it.visibility == LobbyVisibility.PUBLIC }.toList()
    fun getLobby(lobbyCode: String): LobbyState = lobbies[lobbyCode] ?: throw IllegalArgumentException(LOBBY_NOT_FOUND)

    private fun generateUniqueCode(): String {
        repeat(50) {
            val code = Random.nextInt(1000, 10000).toString()
            if (!lobbies.containsKey(code)) return code
        }
        throw IllegalStateException("Eindeutiger Code erschöpft")
    }

    @Scheduled(fixedRate = 30000)
    fun cleanupInactiveLobbies() {
        val now = System.currentTimeMillis()
        lobbies.keys.forEach { code ->
            messagingService.getLobbyLock(code).withLock {
                val lobby = lobbies[code] ?: return@withLock
                val lastSeen = lastActivity[code] ?: 0
                val inactivityPeriod = now - lastSeen

                if (lobby.players.isEmpty() && inactivityPeriod > 60000) {
                    deleteLobbyInternal(code, "timeout_empty")
                } else if (inactivityPeriod > 300000) {
                    deleteLobbyInternal(code, "timeout_inactive")
                }
            }
        }
    }

    fun updateActivity(lobbyCode: String) {
        lastActivity[lobbyCode] = System.currentTimeMillis()
    }
}
