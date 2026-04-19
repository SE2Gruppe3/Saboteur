package com.aau.server.service

import com.aau.saboteur.model.LobbyPlayer
import com.aau.saboteur.model.LobbyState
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Service
class LobbyService {

    private val lobbies = ConcurrentHashMap<String, LobbyState>()

    fun createLobby(playerName: String): LobbyState {
        val code = generateUniqueCode()

        val host = LobbyPlayer(
            id = UUID.randomUUID().toString(),
            name = playerName,
            isReady = false,
            isHost = true
        )

        val lobby = LobbyState(
            lobbyCode = code,
            hostName = playerName,
            players = listOf(host),
            maxPlayers = 10,
            gameStarted = false,
            minPlayersToStart = 3
        )

        lobbies[code] = lobby
        return lobby
    }

    fun joinLobby(lobbyCode: String, playerName: String): LobbyState {
        val lobby = lobbies[lobbyCode] ?: throw IllegalArgumentException("Lobby not found")

        if (lobby.players.size >= lobby.maxPlayers) {
            throw IllegalStateException("Lobby is full")
        }

        val newPlayer = LobbyPlayer(
            id = UUID.randomUUID().toString(),
            name = playerName,
            isReady = false,
            isHost = false
        )

        val updated = lobby.copy(players = lobby.players + newPlayer)
        lobbies[lobbyCode] = updated
        return updated
    }

    fun getLobby(lobbyCode: String): LobbyState =
        lobbies[lobbyCode] ?: throw IllegalArgumentException("Lobby not found")

    private fun generateUniqueCode(): String {
        repeat(50) {
            val code = Random.nextInt(1000, 10000).toString()
            if (!lobbies.containsKey(code)) return code
        }
        throw IllegalStateException("Could not generate unique lobby code")
    }
}