package com.aau.server.service

import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
import com.aau.server.model.LobbyEntity
import com.aau.server.repository.LobbyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private const val LOBBY_NOT_FOUND = "Lobby not found"

@Service
class LobbyService(
    private val lobbyRepository: LobbyRepository,
    private val objectMapper: ObjectMapper
) {

    private val lobbies = ConcurrentHashMap<String, LobbyState>()

    @PostConstruct
    fun loadFromDb() {
        lobbyRepository.findAll().forEach { entity ->
            val players: List<Player> = objectMapper.readValue(entity.playersJson)
            lobbies[entity.lobbyCode] = LobbyState(
                lobbyCode = entity.lobbyCode,
                hostId = entity.hostId,
                players = players,
                gameStarted = entity.gameStarted
            )
        }
    }

    private fun persist(lobby: LobbyState) {
        val entity = LobbyEntity(
            lobbyCode = lobby.lobbyCode,
            hostId = lobby.hostId,
            gameStarted = lobby.gameStarted,
            playersJson = objectMapper.writeValueAsString(lobby.players)
        )
        lobbyRepository.save(entity)
    }

    fun createLobby(playerName: String): LobbyState {
        val code = generateUniqueCode()

        val host = Player(
            id = UUID.randomUUID().toString(),
            name = playerName
        )

        val lobby = LobbyState(
            lobbyCode = code,
            hostId = host.id,
            players = listOf(host),
            gameStarted = false
        )

        lobbies[code] = lobby
        persist(lobby)
        return lobby
    }

    fun joinLobby(lobbyCode: String, playerName: String): LobbyState {
        val lobby = lobbies[lobbyCode] ?: throw IllegalArgumentException(LOBBY_NOT_FOUND)
        require(lobby.players.size < 10) { "Lobby is full" }
        require(!lobby.gameStarted) { "Game has already started" }

        val newPlayer = Player(
            id = UUID.randomUUID().toString(),
            name = playerName
        )

        val updatedLobby = lobby.copy(players = lobby.players + newPlayer)
        lobbies[lobbyCode] = updatedLobby
        persist(updatedLobby)
        return updatedLobby
    }

    fun leaveLobby(lobbyCode: String, playerId: String): LobbyState? {
        val currentLobby = lobbies[lobbyCode] ?: throw IllegalArgumentException(LOBBY_NOT_FOUND)
        
        val updatedPlayers = currentLobby.players.filter { it.id != playerId }
        
        if (updatedPlayers.isEmpty()) {
            lobbies.remove(lobbyCode)
            lobbyRepository.deleteById(lobbyCode)
            return null
        }

        var newHostId = currentLobby.hostId
        if (currentLobby.hostId == playerId) {
            newHostId = updatedPlayers.first().id
        }

        val updatedLobby = currentLobby.copy(
            players = updatedPlayers,
            hostId = newHostId
        )
        
        lobbies[lobbyCode] = updatedLobby
        persist(updatedLobby)
        return updatedLobby
    }

    fun markGameStarted(lobbyCode: String): LobbyState {
        val lobby = lobbies[lobbyCode] ?: throw IllegalArgumentException(LOBBY_NOT_FOUND)
        val updatedLobby = lobby.copy(gameStarted = true)
        lobbies[lobbyCode] = updatedLobby
        persist(updatedLobby)
        return updatedLobby
    }

    fun getAllLobbies(): List<LobbyState> = lobbies.values.toList()

    fun getLobby(lobbyCode: String): LobbyState =
        lobbies[lobbyCode] ?: throw IllegalArgumentException(LOBBY_NOT_FOUND)

    private fun generateUniqueCode(): String {
        repeat(50) {
            val code = Random.nextInt(1000, 10000).toString()
            if (!lobbies.containsKey(code)) return code
        }
        throw IllegalStateException("Could not generate unique lobby code")
    }
}
