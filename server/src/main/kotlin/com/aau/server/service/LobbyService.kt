package com.aau.server.service

import com.aau.saboteur.model.LobbyState
import com.aau.saboteur.model.Player
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private const val LOBBY_NOT_FOUND = "Lobby not found"

@Service
class LobbyService {

    private val lobbies = ConcurrentHashMap<String, LobbyState>()

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
        return lobby
    }

    fun joinLobby(lobbyCode: String, playerName: String): LobbyState {
        return lobbies.compute(lobbyCode) { _, lobby ->
            requireNotNull(lobby) { LOBBY_NOT_FOUND }
            require(lobby.players.size < 10) { "Lobby is full" }
            require(!lobby.gameStarted) { "Game has already started" }

            val newPlayer = Player(
                id = UUID.randomUUID().toString(),
                name = playerName
            )

            lobby.copy(players = lobby.players + newPlayer)
        } ?: throw IllegalArgumentException(LOBBY_NOT_FOUND)
    }

    fun leaveLobby(lobbyCode: String, playerId: String): LobbyState? {
        val currentLobby = lobbies[lobbyCode] ?: throw IllegalArgumentException(LOBBY_NOT_FOUND)
        
        val updatedPlayers = currentLobby.players.filter { it.id != playerId }
        
        if (updatedPlayers.isEmpty()) {
            lobbies.remove(lobbyCode)
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
        return updatedLobby
    }

    fun markGameStarted(lobbyCode: String): LobbyState {
        return lobbies.compute(lobbyCode) { _, lobby ->
            requireNotNull(lobby) { LOBBY_NOT_FOUND }
            lobby.copy(gameStarted = true)
        } ?: throw IllegalArgumentException(LOBBY_NOT_FOUND)
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
