package com.aau.server.service

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.GameState
import com.aau.saboteur.model.MapResult
import com.aau.saboteur.model.PlacedTunnelCard
import com.aau.saboteur.model.Player
import com.aau.saboteur.model.PlayerTurn
import com.aau.saboteur.model.ToolType
import com.aau.saboteur.model.TunnelCard
import com.aau.saboteur.model.blockedTool
import com.aau.saboteur.model.isBlockCard
import com.aau.saboteur.model.isGoalCardType
import com.aau.saboteur.model.isMapCard
import com.aau.saboteur.model.isPathCardType
import com.aau.saboteur.model.isRepairCard
import com.aau.saboteur.model.isRockfallCard
import com.aau.saboteur.model.repairableTools
import com.aau.server.game.CardDeck
import com.aau.server.model.GameStartResult
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

    // Stores the current hand cards for each player.
    private val handsByPlayer = AtomicReference<Map<String, List<TunnelCard>>>(emptyMap())

    // Stores the remaining draw pile.
    private val drawPile = AtomicReference<List<TunnelCard>>(emptyList())

    // Stores all discarded or played action cards.
    private val discardPile = AtomicReference<List<TunnelCard>>(emptyList())

    // Stores privately revealed goal cards for each player after using a map card.
    private val knownGoalsByPlayer =
        AtomicReference<Map<String, Map<BoardPosition, TunnelCard>>>(emptyMap())

    fun getGameState(): GameState = currentState.get()

    fun getPlayer(id: String): Player? = playerData.get()[id]

    fun getHandForPlayer(playerId: String): List<TunnelCard> = getHand(playerId)

    fun getDrawPile(): List<TunnelCard> = drawPile.get()

    fun getDiscardPile(): List<TunnelCard> = discardPile.get()

    fun getKnownGoalsForPlayer(playerId: String): Map<BoardPosition, TunnelCard> =
        knownGoalsByPlayer.get()[playerId].orEmpty()

    fun setHandForPlayer(playerId: String, hand: List<TunnelCard>) {
        setHand(playerId, hand)
    }

    fun setBoardPlacementsForTest(placements: List<PlacedTunnelCard>) {
        currentState.set(currentState.get().copy(boardPlacements = placements))
    }

    fun setPlayersForTest(players: List<PlayerTurn>) {
        currentState.set(currentState.get().copy(players = players))
    }

    fun startGame(players: List<Player>): GameStartResult {
        validatePlayerCount(players.size)

        val gameState = assignRandomTurnOrder(players)
        val assignedPlayers = assignRandomRoles(players)
        val distribution = CardDistributor.distribute(players.map { it.id })

        handsByPlayer.set(distribution.hands)
        drawPile.set(distribution.drawPile)
        discardPile.set(emptyList())
        knownGoalsByPlayer.set(
            players.associate { player -> player.id to emptyMap() }
        )

        return GameStartResult(
            gameState = gameState,
            playerRoles = assignedPlayers,
            cardDistribution = distribution
        )
    }

    /**
     * Plays a blocking card on a target player and updates the game state.
     */
    fun playBlockCard(playerId: String, cardId: String, targetPlayerId: String) {
        val gameState = currentState.get()
        require(gameState.currentPlayerId != null) { "No active player" }
        require(gameState.currentPlayerId == playerId) { "It is not this player's turn" }

        val card = findCardInHand(playerId, cardId)
            ?: throw IllegalArgumentException("Card not found in player's hand")

        require(card.type.isBlockCard()) { "Card is not a blocking card" }

        val toolToBlock = card.type.blockedTool()
            ?: throw IllegalArgumentException("Blocking card has no blocked tool")

        val targetPlayer = gameState.players.find { it.playerId == targetPlayerId }
            ?: throw IllegalArgumentException("Target player not found")

        require(toolToBlock !in targetPlayer.blockedTools) {
            "Target player is already blocked on this tool"
        }

        removeCardFromHand(playerId, cardId)
        val updatedBlockedTools = targetPlayer.blockedTools + toolToBlock
        setBlockedTools(targetPlayerId, updatedBlockedTools)

        discard(card)

        val drawnCard = drawCard()
        if (drawnCard != null) {
            setHand(playerId, getHand(playerId) + drawnCard)
        }

        advanceTurn()
    }

    /**
     * Plays a repair card on a target player and removes one blocked tool.
     */
    fun playRepairCard(playerId: String, cardId: String, targetPlayerId: String, tool: ToolType) {
        val gameState = currentState.get()
        require(gameState.currentPlayerId != null) { "No active player" }
        require(gameState.currentPlayerId == playerId) { "It is not this player's turn" }

        val card = findCardInHand(playerId, cardId)
            ?: throw IllegalArgumentException("Card not found in player's hand")

        require(card.type.isRepairCard()) { "Card is not a repair card" }
        require(tool in card.type.repairableTools()) {
            "Repair card cannot repair the selected tool"
        }

        val targetPlayer = gameState.players.find { it.playerId == targetPlayerId }
            ?: throw IllegalArgumentException("Target player not found")

        require(tool in targetPlayer.blockedTools) {
            "Target player is not blocked on this tool"
        }

        removeCardFromHand(playerId, cardId)
        val updatedBlockedTools = targetPlayer.blockedTools - tool
        setBlockedTools(targetPlayerId, updatedBlockedTools)

        discard(card)

        val drawnCard = drawCard()
        if (drawnCard != null) {
            setHand(playerId, getHand(playerId) + drawnCard)
        }

        advanceTurn()
    }

    /**
     * Plays a map card, reveals a goal card privately, and returns the private result.
     */
    fun playMapCard(playerId: String, cardId: String, targetPosition: BoardPosition): MapResult {
        val gameState = currentState.get()
        require(gameState.currentPlayerId != null) { "No active player" }
        require(gameState.currentPlayerId == playerId) { "It is not this player's turn" }

        val card = findCardInHand(playerId, cardId)
            ?: throw IllegalArgumentException("Card not found in player's hand")

        require(card.type.isMapCard()) { "Card is not a map card" }

        val targetPlacement = gameState.boardPlacements.find { it.position == targetPosition }
            ?: throw IllegalArgumentException("No card found at target position")

        require(targetPlacement.card.type.isGoalCardType()) {
            "Map card can only be used on goal cards"
        }

        val updatedKnownGoals =
            knownGoalsByPlayer.get()[playerId].orEmpty() + (targetPosition to targetPlacement.card)
        knownGoalsByPlayer.set(
            knownGoalsByPlayer.get() + (playerId to updatedKnownGoals)
        )

        removeCardFromHand(playerId, cardId)
        discard(card)

        val drawnCard = drawCard()
        if (drawnCard != null) {
            setHand(playerId, getHand(playerId) + drawnCard)
        }

        advanceTurn()

        return MapResult(
            position = targetPosition,
            card = targetPlacement.card
        )
    }

    /**
     * Plays a rockfall card and removes a path card from the board.
     */
    fun playRockfallCard(playerId: String, cardId: String, targetPosition: BoardPosition) {
        val gameState = currentState.get()
        require(gameState.currentPlayerId != null) { "No active player" }
        require(gameState.currentPlayerId == playerId) { "It is not this player's turn" }

        val card = findCardInHand(playerId, cardId)
            ?: throw IllegalArgumentException("Card not found in player's hand")

        require(card.type.isRockfallCard()) { "Card is not a rockfall card" }

        val targetPlacement = gameState.boardPlacements.find { it.position == targetPosition }
            ?: throw IllegalArgumentException("No card found at target position")

        require(targetPlacement.card.type.isPathCardType()) {
            "Rockfall can only remove normal path cards"
        }

        removeCardFromHand(playerId, cardId)
        removeBoardCard(targetPosition)
        discard(card)

        val drawnCard = drawCard()
        if (drawnCard != null) {
            setHand(playerId, getHand(playerId) + drawnCard)
        }

        advanceTurn()
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

        val gameState = GameState(
            players = randomizedPlayers,
            currentPlayerId = randomizedPlayers.firstOrNull()?.playerId,
            boardPlacements = createInitialBoardPlacements()
        )

        currentState.set(gameState)
        return gameState
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

    /**
     * Assigns each player a random role at game start based on the number of players.
     * The roles are stored in the server's player data and not in the public GameState.
     */
    private fun assignRandomRoles(players: List<Player>): Map<String, Player> {
        val playerIds = players.map { it.id }
        val roles = RoleDistributor.distributeRoles(playerIds)

        val updatedPlayerData = players.associate { player ->
            val updatedPlayer = player.copy(role = roles[player.id])
            player.id to updatedPlayer
        }

        playerData.set(updatedPlayerData)
        return updatedPlayerData
    }

    // Returns the current hand of the given player.
    private fun getHand(playerId: String): List<TunnelCard> =
        handsByPlayer.get()[playerId] ?: emptyList()

    // Finds a card by id in the player's hand without removing it.
    private fun findCardInHand(playerId: String, cardId: String): TunnelCard? =
        getHand(playerId).find { it.id == cardId }

    // Replaces the current hand of the given player.
    private fun setHand(playerId: String, hand: List<TunnelCard>) {
        handsByPlayer.set(
            handsByPlayer.get() + (playerId to hand)
        )
    }

    // Removes a card by id from the player's hand and returns it, or null if not found.
    private fun removeCardFromHand(playerId: String, cardId: String): TunnelCard? {
        val currentHand = getHand(playerId)
        val card = currentHand.find { it.id == cardId } ?: return null
        setHand(playerId, currentHand.filterNot { it.id == cardId })
        return card
    }

    // Draws the top card from the draw pile and removes it from the pile.
    private fun drawCard(): TunnelCard? {
        val currentDrawPile = drawPile.get()
        val nextCard = currentDrawPile.firstOrNull() ?: return null
        drawPile.set(currentDrawPile.drop(1))
        return nextCard
    }

    // Adds a card to the discard pile.
    private fun discard(card: TunnelCard) {
        discardPile.set(discardPile.get() + card)
    }

    // Updates the blocked tools of a player inside the public game state.
    private fun setBlockedTools(playerId: String, blockedTools: Set<ToolType>) {
        val updatedPlayers = currentState.get().players.map { playerTurn ->
            if (playerTurn.playerId == playerId) {
                playerTurn.copy(blockedTools = blockedTools)
            } else {
                playerTurn
            }
        }

        currentState.set(
            currentState.get().copy(players = updatedPlayers)
        )
    }

    // Removes a placed board card at the given position.
    private fun removeBoardCard(targetPosition: BoardPosition) {
        val gameState = currentState.get()
        val updatedPlacements = gameState.boardPlacements.filterNot { it.position == targetPosition }

        currentState.set(
            gameState.copy(boardPlacements = updatedPlacements)
        )
    }

    // Advances the turn to the next player in turn order.
    private fun advanceTurn() {
        val gameState = currentState.get()
        val playersInOrder = gameState.players.sortedBy { it.turnOrder }

        if (playersInOrder.isEmpty()) return

        val currentIndex = playersInOrder.indexOfFirst { it.playerId == gameState.currentPlayerId }
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % playersInOrder.size
        val nextPlayerId = playersInOrder[nextIndex].playerId

        currentState.set(
            gameState.copy(currentPlayerId = nextPlayerId)
        )
    }
}