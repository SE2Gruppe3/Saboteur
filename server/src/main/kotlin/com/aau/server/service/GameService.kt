package com.aau.server.service

import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.PlayerTurn
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference

@Service
class GameService {

    private val currentState = AtomicReference(
        GameState(
            players = emptyList(),
            currentPlayerId = null
        )
    )

    fun getGameState(): GameState = currentState.get()

    fun assignRandomTurnOrder(players: List<Player>): GameState {
        val randomizedPlayers = players
            .shuffled()
            .mapIndexed { index, player ->
                PlayerTurn(
                    playerId = player.id,
                    playerName = player.name,
                    turnOrder = index + 1
                )
            }

        val gameState = GameState(
            players = randomizedPlayers,
            currentPlayerId = randomizedPlayers.firstOrNull()?.playerId
        )

        currentState.set(gameState)
        return gameState
    }
}