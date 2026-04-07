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

    private val playerData = AtomicReference<Map<String, Player>>(emptyMap())

    fun getGameState(): GameState = currentState.get()

    fun getPlayer(id: String): Player? = playerData.get()[id]

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

    /**
     * Assigns each player a random role at game start based on the number of players.
     * The roles are stored in the server's player data and not in the public GameState.
     */
    fun assignRandomRoles(players: List<Player>): Map<String, Player> {
        val playerIds = players.map { it.id }
        val roles = RoleDistributor.distributeRoles(playerIds)

        val updatedPlayerData = players.associate { player ->
            val updatedPlayer = player.copy(role = roles[player.id])
            player.id to updatedPlayer
        }

        playerData.set(updatedPlayerData)
        return updatedPlayerData
    }
}