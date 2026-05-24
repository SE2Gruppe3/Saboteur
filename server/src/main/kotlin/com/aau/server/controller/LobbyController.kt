package com.aau.server.controller

import com.aau.saboteur.model.*
import com.aau.server.service.GameService
import com.aau.server.service.LobbyService
import com.aau.server.service.TurnManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/lobby")
@CrossOrigin(origins = ["*"])
class LobbyController(
    private val lobbyService: LobbyService,
    private val gameService: GameService,
    private val turnManager: TurnManager
) {

    @PostMapping("/create")
    fun createSession(@RequestBody request: LobbyCreateRequest): ResponseEntity<ReconnectResponse> {
        val lobby = lobbyService.createLobby(request.playerName, request.playerId, request.visibility)
        return ResponseEntity.ok(ReconnectResponse(
            myPlayerId = lobby.hostId,
            lobbyState = lobby
        ))
    }

    @PostMapping("/join")
    fun joinSession(@RequestBody request: LobbyJoinRequest): ResponseEntity<ReconnectResponse> {
        val lobby = lobbyService.joinLobby(request.lobbyCode, request.playerName, request.playerId)
        val finalPlayerId = request.playerId ?: lobby.players.last().id
        return buildReconnectResponse(finalPlayerId, lobby)
    }

    @PostMapping("/reconnect")
    fun reconnect(@RequestBody request: ReconnectRequest): ResponseEntity<ReconnectResponse> {
        val lobby = try {
            lobbyService.getLobby(request.lobbyCode)
        } catch (e: Exception) {
            return ResponseEntity.notFound().build()
        }
        
        if (lobby.players.none { it.id == request.playerId }) {
            return ResponseEntity.status(403).build()
        }

        return buildReconnectResponse(request.playerId, lobby)
    }

    private fun buildReconnectResponse(playerId: String, lobby: LobbyState): ResponseEntity<ReconnectResponse> {
        var gameState: GameState? = null
        var hand: List<TunnelCard> = emptyList()
        var role: Role? = null

        if (lobby.gameStarted) {
            gameState = try { turnManager.getGameState(lobby.lobbyCode) } catch (e: Exception) { null }
            hand = turnManager.getHands(lobby.lobbyCode)[playerId] ?: emptyList()
            role = gameService.getPlayer(lobby.lobbyCode, playerId)?.role
        }

        return ResponseEntity.ok(ReconnectResponse(
            myPlayerId = playerId,
            lobbyState = lobby,
            gameState = gameState,
            playerHand = hand,
            playerRole = role
        ))
    }
}
