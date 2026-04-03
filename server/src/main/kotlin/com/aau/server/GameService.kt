package com.aau.server

import com.aau.shared.game.GameState
import com.aau.shared.game.Player
import com.aau.shared.game.PlayerTurn
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
