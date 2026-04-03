package com.aau.server

import com.aau.shared.game.CreateGameRequest
import com.aau.shared.game.GameState
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/game")
class GameController(
    private val gameService: GameService
) {

    @GetMapping
    fun getGameState(): Map<String, Any?> {
        val state = gameService.getGameState()
        return state.toApiMap()
    }

    @PostMapping("/start")
    fun startGame(@RequestBody request: CreateGameRequest): Map<String, Any?> {
        val state = gameService.assignRandomTurnOrder(request.players)
        return state.toApiMap()
    }
}

private fun GameState.toApiMap(): Map<String, Any?> {
    return mapOf(
        "players" to players.map { player ->
            mapOf(
                "playerId" to player.playerId,
                "playerName" to player.playerName,
                "turnOrder" to player.turnOrder
            )
        },
        "currentPlayerId" to currentPlayerId
    )
}
