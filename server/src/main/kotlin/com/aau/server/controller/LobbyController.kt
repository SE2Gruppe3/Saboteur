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
        val lobby = lobbyService.createLobby(request.playerName)
        val hostId = lobby.hostId
        return ResponseEntity.ok(ReconnectResponse(
            myPlayerId = hostId,
            lobbyState = lobby
        ))
    }

    @PostMapping("/join")
    fun joinSession(@RequestBody request: LobbyJoinRequest): ResponseEntity<ReconnectResponse> {
        val lobby = lobbyService.joinLobby(request.lobbyCode, request.playerName)
        val newPlayerId = lobby.players.last().id
        return ResponseEntity.ok(ReconnectResponse(
            myPlayerId = newPlayerId,
            lobbyState = lobby
        ))
    }

    @PostMapping("/reconnect")
    fun reconnect(@RequestBody request: ReconnectRequest): ResponseEntity<ReconnectResponse> {
        val lobby = try {
            lobbyService.getLobby(request.lobbyCode)
        } catch (e: Exception) {
            return ResponseEntity.notFound().build()
        }
        
        val playerInLobby = lobby.players.find { it.id == request.playerId }
            ?: return ResponseEntity.status(403).build()

        var gameState: GameState? = null
        var hand: List<TunnelCard> = emptyList()
        var role: Role? = null

        if (lobby.gameStarted) {
            gameState = turnManager.getGameState(request.lobbyCode)
            hand = turnManager.getHands(request.lobbyCode)[request.playerId] ?: emptyList()
            role = gameService.getPlayer(request.lobbyCode, request.playerId)?.role
        }

        return ResponseEntity.ok(ReconnectResponse(
            myPlayerId = request.playerId,
            lobbyState = lobby,
            gameState = gameState,
            playerHand = hand,
            playerRole = role
        ))
    }
}
