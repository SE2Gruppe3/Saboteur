package com.aau.server.service

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.PlacedTunnelCard
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.PlayerTurn
import com.aau.server.game.CardDeck
import com.aau.server.model.GameStartResult
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class GameService {

    // Roles and full player data per lobby
    private val lobbyPlayerData = ConcurrentHashMap<String, Map<String, Player>>()

    fun getPlayer(lobbyCode: String, playerId: String): Player? = 
        lobbyPlayerData[lobbyCode]?.get(playerId)

    fun setPlayerData(lobbyCode: String, players: Map<String, Player>) {
        lobbyPlayerData[lobbyCode] = players
    }

    fun getAllPlayerData(lobbyCode: String): Map<String, Player> = 
        lobbyPlayerData[lobbyCode] ?: emptyMap()

    fun removePlayerData(lobbyCode: String) {
        lobbyPlayerData.remove(lobbyCode)
    }

    fun startGame(players: List<Player>): GameStartResult {
        validatePlayerCount(players.size)

        val gameState = assignRandomTurnOrder(players)
        val assignedPlayers = assignRandomRoles(players)
        val distribution = CardDistributor.distribute(players.map { it.id })

        return GameStartResult(
            gameState = gameState,
            playerRoles = assignedPlayers,
            cardDistribution = distribution
        )
    }

    private fun validatePlayerCount(playerCount: Int) {
        require(playerCount in 3..10) {
            "Game requires between 3 and 10 players"
        }
    }

    private fun assignRandomTurnOrder(players: List<Player>): GameState {
        val randomizedPlayers = players
            .shuffled()
            .mapIndexed { index, player ->
                PlayerTurn(
                    playerId = player.id,
                    playerName = player.name,
                    turnOrder = index + 1
                )
            }

        return GameState(
            players = randomizedPlayers,
            currentPlayerId = randomizedPlayers.firstOrNull()?.playerId,
            boardPlacements = createInitialBoardPlacements()
        )
    }

    private fun createInitialBoardPlacements(): List<PlacedTunnelCard> {
        val goalCards = CardDeck.createGoalCards().shuffled()
        return listOf(
            PlacedTunnelCard(
                position = BoardPosition(row = 2, column = 10),
                card = goalCards[0]
            ),
            PlacedTunnelCard(
                position = BoardPosition(row = 4, column = 10),
                card = goalCards[1]
            ),
            PlacedTunnelCard(
                position = BoardPosition(row = 6, column = 10),
                card = goalCards[2]
            ),
            PlacedTunnelCard(
                position = BoardPosition(row = 4, column = 2),
                card = CardDeck.createStartCard()
            )
        )
    }

    private fun assignRandomRoles(players: List<Player>): Map<String, Player> {
        val playerIds = players.map { it.id }
        val roles = RoleDistributor.distributeRoles(playerIds)

        return players.associate { player ->
            val updatedPlayer = player.copy(role = roles[player.id])
            player.id to updatedPlayer
        }
    }
}