package com.aau.server.service

import com.aau.saboteur.model.LobbyPlayer
import com.aau.saboteur.model.LobbyState
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Service
class LobbyService(
    private val messagingService: MessagingService
) {
    private data class Lobby(
        val code: String,
        val hostPlayerId: String,
        val maxPlayers: Int = 10,
        val minPlayersToStart: Int = 3,
        val playersById: MutableMap<String, LobbyPlayer> = LinkedHashMap(),
        val sessionIdsByPlayerId: MutableMap<String, String> = LinkedHashMap()
    )

    private val lobbies = ConcurrentHashMap<String, Lobby>()

    fun createLobby(session: WebSocketSession, playerName: String): LobbyState {
        val code = generateCode()
        val playerId = newPlayerId()

        val host = LobbyPlayer(
            id = playerId,
            name = playerName,
            isReady = false,
            isHost = true
        )

        val lobby = Lobby(code = code, hostPlayerId = playerId)
        lobby.playersById[playerId] = host
        lobby.sessionIdsByPlayerId[playerId] = session.id

        lobbies[code] = lobby

        val state = toState(lobby)
        messagingService.sendToSession(session.id, "LOBBY_STATE_UPDATE", state)
        return state
    }

    fun joinLobby(session: WebSocketSession, lobbyCode: String, playerName: String): LobbyState {
        val lobby = lobbies[lobbyCode] ?: throw IllegalArgumentException("Lobby not found")

        if (lobby.playersById.size >= lobby.maxPlayers) {
            throw IllegalStateException("Lobby is full")
        }

        val playerId = newPlayerId()
        val player = LobbyPlayer(
            id = playerId,
            name = playerName,
            isReady = false,
            isHost = false
        )

        lobby.playersById[playerId] = player
        lobby.sessionIdsByPlayerId[playerId] = session.id

        val state = toState(lobby)

        // update everyone in this lobby
        messagingService.sendToSessions(lobby.sessionIdsByPlayerId.values, "LOBBY_STATE_UPDATE", state)
        return state
    }

    private fun toState(lobby: Lobby): LobbyState {
        val hostName = lobby.playersById[lobby.hostPlayerId]?.name ?: ""
        return LobbyState(
            lobbyCode = lobby.code,
            hostName = hostName,
            players = lobby.playersById.values.toList(),
            maxPlayers = lobby.maxPlayers,
            gameStarted = false,
            minPlayersToStart = lobby.minPlayersToStart
        )
    }

    private fun generateCode(): String {
        while (true) {
            val code = (1000..9999).random().toString()
            if (!lobbies.containsKey(code)) return code
        }
    }

    private fun newPlayerId(): String = "p_${Random.nextInt(100000, 999999)}"
}