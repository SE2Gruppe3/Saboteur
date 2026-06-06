package com.aau.server.service

import com.aau.saboteur.model.*
import com.aau.server.game.*
import com.aau.server.model.*
import com.aau.server.repository.GameRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap



private val GOAL_POSITION_1 = BoardPosition(row = 2, column = 10)
private val GOAL_POSITION_2 = BoardPosition(row = 4, column = 10)
private val GOAL_POSITION_3 = BoardPosition(row = 6, column = 10)
private val START_POSITION = BoardPosition(row = 4, column = 2)


@Service
class TurnManager(
    private val gameRepository: GameRepository,
    private val objectMapper: ObjectMapper,
    private val gameService: GameService
) {
    private val logger = LoggerFactory.getLogger(TurnManager::class.java)

    companion object {
        const val BOARD_ROWS = 9
        const val BOARD_COLUMNS = 13

        const val MAX_ROUNDS = 3
        const val SABOTEUR_GOLD_PER_1_PLAYER = 4
        const val SABOTEUR_GOLD_PER_2_PLAYERS = 3
        const val SABOTEUR_GOLD_PER_3_PLAYERS = 2
        const val SABOTEUR_GOLD_PER_4_PLAYERS = 1


    }

    private data class GameInternalState(
        var hands: Map<String, MutableList<TunnelCard>>,
        var drawPile: MutableList<TunnelCard>,
        var discardPile: MutableList<TunnelCard> = mutableListOf(),
        var deckWasEmptied: Boolean = false,
        var passedSinceEmpty: Int = 0,
        var gameState: GameState,
        var knownGoalsByPlayer: MutableMap<String, MutableMap<BoardPosition, TunnelCard>> = mutableMapOf(),
        var goldDeck: MutableList<GoldCard> = mutableListOf(),
        var lastPlayerWhoPlayed: String? = null
    )

    private val games = ConcurrentHashMap<String, GameInternalState>()

    @Transactional
    fun loadFromDb(): Int {
        val allEntities = gameRepository.findAll()
        allEntities.forEach { entity ->
            try {
                val hands: Map<String, List<TunnelCard>> = objectMapper.readValue(entity.handsJson)
                val drawPile: List<TunnelCard> = objectMapper.readValue(entity.drawPileJson)
                val discardPile: List<TunnelCard> =
                    if (entity.discardPileJson.isNotEmpty()) objectMapper.readValue(entity.discardPileJson)
                    else mutableListOf()
                val playersTurn: List<PlayerTurn> = objectMapper.readValue(entity.playersTurnJson)
                val board: List<PlacedTunnelCard> = objectMapper.readValue(entity.boardJson)
                val playerRoles: Map<String, Player> = objectMapper.readValue(entity.playerRolesJson)

                val lastRoundResult: RoundResult? = try {
                    if (entity.lastRoundResultJson.isNotBlank()) {
                        objectMapper.readValue(entity.lastRoundResultJson)
                    } else null
                } catch (e: Exception) {
                    logger.warn("Could not parse lastRoundResult for game {}, using null", entity.lobbyCode)
                    null
                }

                val goldDeck: List<GoldCard> = try {
                    if (entity.goldDeckJson.isNotBlank()) {
                        objectMapper.readValue(entity.goldDeckJson)
                    } else {
                        CardDeck.createGoldDeck().shuffled()
                    }
                } catch (e: Exception) {
                    logger.warn("Could not parse goldDeck for game {}, creating new gold deck", entity.lobbyCode)
                    CardDeck.createGoldDeck().shuffled()
                }

                val knownGoalsByPlayer: MutableMap<String, MutableMap<BoardPosition, TunnelCard>> = try {
                    if (!entity.knownGoalsByPlayerJson.isNullOrBlank()) {
                        val raw: Map<String, Map<String, TunnelCard>> =
                            objectMapper.readValue(entity.knownGoalsByPlayerJson)
                        raw.mapValues { (_, innerMap) ->
                            innerMap.mapKeys { BoardPosition.fromString(it.key) }.toMutableMap()
                        }.toMutableMap()
                    } else mutableMapOf()
                } catch (e: Exception) {
                    logger.warn("Could not parse knownGoals for game {}, using empty map", entity.lobbyCode)
                    mutableMapOf()
                }

                val internal = GameInternalState(
                    hands = hands.mapValues { it.value.toMutableList() },
                    drawPile = drawPile.toMutableList(),
                    discardPile = discardPile.toMutableList(),
                    deckWasEmptied = entity.deckWasEmptied,
                    passedSinceEmpty = entity.passedSinceEmpty,
                    gameState = GameState(
                        players = playersTurn,
                        currentPlayerId = entity.currentPlayerId,
                        boardPlacements = board,
                        deckSize = drawPile.size,
                        currentRound = entity.currentRound,
                        isRoundOver = entity.isRoundOver,
                        isGameOver = entity.isGameOver,
                        lastRoundResult = lastRoundResult
                    ),
                    knownGoalsByPlayer = knownGoalsByPlayer,
                    goldDeck = goldDeck.toMutableList(),
                    lastPlayerWhoPlayed = entity.lastPlayerWhoPlayed
                )

                games[entity.lobbyCode] = internal
                gameService.setPlayerData(entity.lobbyCode, playerRoles)
                updatePlayerGoldValues(entity.lobbyCode, internal)
            } catch (e: Exception) {
                logger.error("Failed to recover game {}: {}", entity.lobbyCode, e.message, e)
            }
        }
        return games.size
    }

    // -- PLAY CARD --
    @Transactional
    fun playCard(
        lobbyCode: String,
        playerId: String,
        cardId: String,
        position: BoardPosition,
        isRotated: Boolean
    ): TurnResult {
        val internal = games[lobbyCode] ?: throw IllegalArgumentException("Spiel nicht gefunden")
        synchronized(internal) {
            val state = internal.gameState
            require(state.currentPlayerId == playerId) { "Du bist nicht am Zug." }
            val currentPlayer = state.players.find { it.playerId == playerId }
                ?: throw IllegalArgumentException("Spieler $playerId nicht gefunden.")
            require(currentPlayer.blockedTools.isEmpty()) { "Geblockte Spieler können keine Tunnelkarten spielen." }
            val playerHand = internal.hands[playerId] ?: throw IllegalArgumentException("Hand nicht gefunden")
            val card = playerHand.find { it.id == cardId } ?: throw IllegalArgumentException("Karte nicht auf der Hand")
            val effectiveCard = if (isRotated) card.rotated180() else card
            require(canPlaceOnBoard(position, effectiveCard, state.boardPlacements)) { "error.invalid_placement" }

            try {
                playerHand.remove(card)
                drawCardForPlayer(internal, playerId)
                internal.passedSinceEmpty = 0
                val placementsWithCard = state.boardPlacements + PlacedTunnelCard(position, effectiveCard)
                val newPlacements = revealGoalCards(position, effectiveCard, placementsWithCard)
                internal.lastPlayerWhoPlayed = playerId
                internal.gameState = state.copy(
                    boardPlacements = newPlacements,
                    currentPlayerId = nextPlayerId(state)
                )

                val winner = determineWinner(lobbyCode, internal.gameState, internal)
                finalizeAndPersist(lobbyCode, internal)
                return TurnResult(
                    internal.gameState,
                    internal.hands.mapValues { it.value.toList() },
                    winner
                )
            } catch (e: Exception) {
                games.remove(lobbyCode)
                throw e
            }
        }
    }

    // -- PLAY BLOCK CARD --
    @Transactional
    fun playBlockCard(lobbyCode: String, playerId: String, cardId: String, targetPlayerId: String): TurnResult {
        val internal = games[lobbyCode] ?: throw IllegalArgumentException("Spiel nicht gefunden")
        synchronized(internal) {
            val state = internal.gameState
            require(state.currentPlayerId == playerId) { "Du bist nicht am Zug." }
            val playerHand = internal.hands[playerId] ?: throw IllegalArgumentException("Hand nicht gefunden")
            val card = playerHand.find { it.id == cardId } ?: throw IllegalArgumentException("Karte nicht gefunden")
            require(card.type.isBlockCard()) { "Keine Sperrkarte." }
            val toolToBlock = card.type.blockedTool() ?: throw IllegalArgumentException("Ungültiges Werkzeug.")
            val targetPlayer = state.players.find { it.playerId == targetPlayerId } ?: throw IllegalArgumentException("Ziel nicht gefunden.")
            require(toolToBlock !in targetPlayer.blockedTools) { "Bereits blockiert." }

            try {
                playerHand.remove(card)
                internal.discardPile.add(card)
                val updatedPlayers = state.players.map {
                    if (it.playerId == targetPlayerId) it.copy(blockedTools = it.blockedTools + toolToBlock) else it
                }
                internal.lastPlayerWhoPlayed = playerId
                internal.gameState = state.copy(
                    players = updatedPlayers,
                    currentPlayerId = nextPlayerId(state)
                )
                drawCardForPlayer(internal, playerId)
                internal.passedSinceEmpty = 0
                val winner = determineWinner(lobbyCode, internal.gameState, internal)
                finalizeAndPersist(lobbyCode, internal)
                return TurnResult(
                    internal.gameState,
                    internal.hands.mapValues { it.value.toList() },
                    winner
                )
            } catch (e: Exception) {
                games.remove(lobbyCode)
                throw e
            }
        }
    }

    // -- PLAY REPAIR CARD --
    @Transactional
    fun playRepairCard(
        lobbyCode: String,
        playerId: String,
        cardId: String,
        targetPlayerId: String,
        tool: ToolType
    ): TurnResult {
        val internal = games[lobbyCode] ?: throw IllegalArgumentException("Spiel nicht gefunden")
        synchronized(internal) {
            val state = internal.gameState
            require(state.currentPlayerId == playerId) { "Du bist nicht am Zug." }
            val playerHand = internal.hands[playerId] ?: throw IllegalArgumentException("Hand nicht gefunden")
            val card = playerHand.find { it.id == cardId } ?: throw IllegalArgumentException("Karte nicht gefunden")
            require(card.type.isRepairCard()) { "Keine Reparaturkarte." }
            require(tool in card.type.repairableTools()) { "Falsches Werkzeug." }
            val targetPlayer = state.players.find { it.playerId == targetPlayerId }
                ?: throw IllegalArgumentException("Ziel nicht gefunden.")
            require(tool in targetPlayer.blockedTools) { "Werkzeug ist nicht blockiert." }

            try {
                playerHand.remove(card)
                internal.discardPile.add(card)
                val updatedPlayers = state.players.map {
                    if (it.playerId == targetPlayerId) it.copy(blockedTools = it.blockedTools - tool) else it
                }
                internal.lastPlayerWhoPlayed = playerId
                internal.gameState = state.copy(
                    players = updatedPlayers,
                    currentPlayerId = nextPlayerId(state)
                )
                drawCardForPlayer(internal, playerId)
                internal.passedSinceEmpty = 0
                val winner = determineWinner(lobbyCode, internal.gameState, internal)
                finalizeAndPersist(lobbyCode, internal)
                return TurnResult(
                    internal.gameState,
                    internal.hands.mapValues { it.value.toList() },
                    winner
                )
            } catch (e: Exception) {
                games.remove(lobbyCode)
                throw e
            }
        }
    }

    // -- PLAY MAP CARD --
    @Transactional
    fun playMapCard(
        lobbyCode: String,
        playerId: String,
        cardId: String,
        targetPosition: BoardPosition
    ): Pair<TurnResult, MapResult> {
        val internal = games[lobbyCode] ?: throw IllegalArgumentException("Spiel nicht gefunden")
        return synchronized(internal) {
            val state = internal.gameState
            require(state.currentPlayerId == playerId) { "Du bist nicht am Zug." }
            val playerHand = internal.hands[playerId] ?: throw IllegalArgumentException("Hand nicht gefunden")
            val card = playerHand.find { it.id == cardId } ?: throw IllegalArgumentException("Karte nicht gefunden")
            require(card.type.isMapCard()) { "Keine Map-Karte." }
            val targetPlacement = state.boardPlacements.find { it.position == targetPosition }
                ?: throw IllegalArgumentException("Keine Karte an Position.")
            require(targetPlacement.card.type.isGoalCardType()) { "Nur auf Zielkarten möglich." }

            try {
                playerHand.remove(card)
                internal.discardPile.add(card)
                internal.knownGoalsByPlayer.getOrPut(playerId) { mutableMapOf() }[targetPosition] = targetPlacement.card
                drawCardForPlayer(internal, playerId)
                internal.lastPlayerWhoPlayed = playerId
                internal.gameState = state.copy(currentPlayerId = nextPlayerId(state))
                internal.passedSinceEmpty = 0
                val winner = determineWinner(lobbyCode, internal.gameState, internal)
                finalizeAndPersist(lobbyCode, internal)
                val res = TurnResult(
                    internal.gameState,
                    internal.hands.mapValues { it.value.toList() },
                    winner
                )
                Pair(res, MapResult(targetPosition, targetPlacement.card))
            } catch (e: Exception) {
                games.remove(lobbyCode)
                throw e
            }
        }
    }

    // -- PLAY ROCKFALL CARD --
    @Transactional
    fun playRockfallCard(lobbyCode: String, playerId: String, cardId: String, targetPosition: BoardPosition): TurnResult {
        val internal = games[lobbyCode] ?: throw IllegalArgumentException("Spiel nicht gefunden")
        synchronized(internal) {
            val state = internal.gameState
            require(state.currentPlayerId == playerId) { "Du bist nicht am Zug." }
            val playerHand = internal.hands[playerId] ?: throw IllegalArgumentException("Hand nicht gefunden")
            val card = playerHand.find { it.id == cardId } ?: throw IllegalArgumentException("Karte nicht gefunden")
            require(card.type.isRockfallCard()) { "Kein Felssturz." }
            val targetPlacement = state.boardPlacements.find { it.position == targetPosition } ?: throw IllegalArgumentException("Keine Karte an Position.")
            require(targetPlacement.card.type.isPathCardType()) { "Darf nur Tunnelkarten entfernen." }

            try {
                playerHand.remove(card)
                internal.discardPile.add(card)
                internal.discardPile.add(targetPlacement.card)
                val updatedPlacements = state.boardPlacements.filterNot { it.position == targetPosition }
                internal.lastPlayerWhoPlayed = playerId
                internal.gameState = state.copy(
                    boardPlacements = updatedPlacements,
                    currentPlayerId = nextPlayerId(state)
                )
                drawCardForPlayer(internal, playerId)
                internal.passedSinceEmpty = 0
                val winner = determineWinner(lobbyCode, internal.gameState, internal)
                finalizeAndPersist(lobbyCode, internal)
                return TurnResult(
                    internal.gameState,
                    internal.hands.mapValues { it.value.toList() },
                    winner
                )
            } catch (e: Exception) {
                games.remove(lobbyCode)
                throw e
            }
        }
    }

    // -- DISCARD CARD --
    @Transactional
    fun discardCard(lobbyCode: String, playerId: String, cardId: String): TurnResult {
        val internal = games[lobbyCode] ?: throw IllegalArgumentException("Spiel nicht gefunden")
        synchronized(internal) {
            require(internal.gameState.currentPlayerId == playerId) { "Du bist nicht am Zug." }
            val playerHand = internal.hands[playerId] ?: throw IllegalArgumentException("Hand nicht gefunden")
            val card = playerHand.find { it.id == cardId } ?: throw IllegalArgumentException("Karte nicht gefunden")

            try {
                playerHand.remove(card)
                internal.discardPile.add(card)
                drawCardForPlayer(internal, playerId)
                if (internal.deckWasEmptied) internal.passedSinceEmpty++
                internal.lastPlayerWhoPlayed = playerId
                internal.gameState = internal.gameState.copy(
                    currentPlayerId = nextPlayerId(internal.gameState)
                )
                val winner = determineWinner(lobbyCode, internal.gameState, internal)
                finalizeAndPersist(lobbyCode, internal)
                return TurnResult(
                    internal.gameState,
                    internal.hands.mapValues { it.value.toList() },
                    winner
                )
            } catch (e: Exception) {
                games.remove(lobbyCode)
                throw e
            }
        }
    }

    private fun finalizeAndPersist(lobbyCode: String, internal: GameInternalState) {
        internal.gameState = internal.gameState.copy(deckSize = internal.drawPile.size)
        persist(lobbyCode)
    }

    private fun persist(lobbyCode: String) {
        val internal = games[lobbyCode] ?: return

        val knownGoalsJson = objectMapper.writeValueAsString(
            internal.knownGoalsByPlayer.mapValues { (_, innerMap) ->
                innerMap.mapKeys { it.key.toString() }
            }
        )

        val lastRoundResultJson = internal.gameState.lastRoundResult?.let {
            objectMapper.writeValueAsString(it)
        } ?: ""

        val goldDeckJson = objectMapper.writeValueAsString(internal.goldDeck)

        val entity = GameEntity(
            lobbyCode = lobbyCode,
            currentPlayerId = internal.gameState.currentPlayerId,
            boardJson = objectMapper.writeValueAsString(internal.gameState.boardPlacements),
            drawPileJson = objectMapper.writeValueAsString(internal.drawPile),
            discardPileJson = objectMapper.writeValueAsString(internal.discardPile),
            handsJson = objectMapper.writeValueAsString(internal.hands),
            playersTurnJson = objectMapper.writeValueAsString(internal.gameState.players),
            playerRolesJson = objectMapper.writeValueAsString(gameService.getAllPlayerData(lobbyCode)),
            knownGoalsByPlayerJson = knownGoalsJson,
            deckWasEmptied = internal.deckWasEmptied,
            passedSinceEmpty = internal.passedSinceEmpty,
            currentRound = internal.gameState.currentRound,
            isRoundOver = internal.gameState.isRoundOver,
            isGameOver = internal.gameState.isGameOver,
            lastRoundResultJson = lastRoundResultJson,
            goldDeckJson = goldDeckJson,
            lastPlayerWhoPlayed = internal.lastPlayerWhoPlayed
        )
        gameRepository.save(entity)
    }

    @Transactional
    fun initializeGame(lobbyCode: String, distribution: CardDistributionResult, initialGameState: GameState) {
        val internal = GameInternalState(
            hands = distribution.hands.mapValues { it.value.toMutableList() },
            drawPile = distribution.drawPile.toMutableList(),
            gameState = initialGameState,
            knownGoalsByPlayer = initialGameState.players
                .associate { it.playerId to mutableMapOf<BoardPosition, TunnelCard>() }
                .toMutableMap(),
            goldDeck = CardDeck.createGoldDeck().shuffled().toMutableList()
        )
        updatePlayerGoldValues(lobbyCode, internal)
        games[lobbyCode] = internal
        finalizeAndPersist(lobbyCode, internal)
    }

    fun getGameStateSnapshot(lobbyCode: String): GameState {
        val internal = games[lobbyCode] ?: throw IllegalArgumentException("Spiel nicht gefunden")
        return synchronized(internal) { internal.gameState }
    }

    fun getGameState(lobbyCode: String): GameState = getGameStateSnapshot(lobbyCode)

    fun getHands(lobbyCode: String): Map<String, List<TunnelCard>> {
        val internal = games[lobbyCode] ?: return emptyMap()
        return synchronized(internal) { internal.hands.mapValues { it.value.toList() } }
    }

    fun getValidPositions(lobbyCode: String, card: TunnelCard, isRotated: Boolean, placements: List<PlacedTunnelCard>): List<BoardPosition> {
        val effectiveCard = if (isRotated) card.rotated180() else card
        val occupied = placements.map { it.position }.toSet()
        val candidates = mutableSetOf<BoardPosition>()
        for (pos in occupied) {
            for (dir in Direction.entries) {
                val neighbor = boardNeighbor(pos, dir)
                if (neighbor !in occupied && neighbor.row in 0 until BOARD_ROWS && neighbor.column in 0 until BOARD_COLUMNS) {
                    candidates.add(neighbor)
                }
            }
        }
        return candidates.filter { pos -> canPlaceOnBoard(pos, effectiveCard, placements) }
    }

    private fun drawCardForPlayer(internal: GameInternalState, playerId: String) {
        if (internal.drawPile.isEmpty()) { internal.deckWasEmptied = true; return }
        internal.hands[playerId]?.add(internal.drawPile.removeFirst())
        if (internal.drawPile.isEmpty()) internal.deckWasEmptied = true
    }


    private fun updatePlayerGoldValues(lobbyCode: String, internal: GameInternalState) {
        val playerData = gameService.getAllPlayerData(lobbyCode)

        val updatedPlayers = internal.gameState.players.map { playerTurn ->
            val player = playerData[playerTurn.playerId]
            val goldValue = (player?.goldCards ?: emptyList()).sumOf { it.value }
            playerTurn.copy(goldValue = goldValue)
        }

        internal.gameState = internal.gameState.copy(players = updatedPlayers)
    }

    private fun drawGoldCards(internal: GameInternalState, amount: Int): List<GoldCard> {
        val drawn = mutableListOf<GoldCard>()
        repeat(amount) {
            if (internal.goldDeck.isNotEmpty()) {
                drawn.add(internal.goldDeck.removeFirst())
            }
        }
        return drawn
    }

    private fun addGoldToPlayer(lobbyCode: String, playerId: String, goldCards: List<GoldCard>) {
        if (goldCards.isEmpty()) return
        val playerData = gameService.getAllPlayerData(lobbyCode).toMutableMap()
        val player = playerData[playerId] ?: return
        playerData[playerId] = player.copy(goldCards = player.goldCards + goldCards)
        gameService.setPlayerData(lobbyCode, playerData)
    }

    private fun getPlayersByRole(lobbyCode: String, role: Role): List<Player> {
        return gameService.getAllPlayerData(lobbyCode).values.filter { it.role == role }
    }

    private fun distributeGoldToGolddiggers(
        lobbyCode: String,
        internal: GameInternalState,
        winningPlayerId: String
    ): Map<String, List<GoldCard>> {
        val golddiggers = getPlayersByRole(lobbyCode, Role.GOLDDIGGER)
            .map { it.id }
            .sortedBy { playerId ->
                val playerTurn = internal.gameState.players.find { it.playerId == playerId }
                playerTurn?.turnOrder ?: Int.MAX_VALUE
            }

        if (golddiggers.isEmpty()) return emptyMap()

        val startIndex = golddiggers.indexOf(winningPlayerId).let { if (it == -1) 0 else it }
        val orderedWinners = golddiggers.drop(startIndex) + golddiggers.take(startIndex)

        val distributed = mutableMapOf<String, List<GoldCard>>()

        orderedWinners.forEach { playerId ->
            val gold = drawGoldCards(internal, 1)
            if (gold.isNotEmpty()) {
                addGoldToPlayer(lobbyCode, playerId, gold)
                distributed[playerId] = gold
            }
        }

        return distributed
    }

    private fun distributeGoldToSaboteurs(
        lobbyCode: String,
        internal: GameInternalState
    ): Map<String, List<GoldCard>> {
        val saboteurs = getPlayersByRole(lobbyCode, Role.SABOTEUR)
            .map { it.id }
            .sortedBy { playerId ->
                val playerTurn = internal.gameState.players.find { it.playerId == playerId }
                playerTurn?.turnOrder ?: Int.MAX_VALUE
            }

        if (saboteurs.isEmpty()) return emptyMap()

        val goldPerSaboteur = when (saboteurs.size) {
            1 -> SABOTEUR_GOLD_PER_1_PLAYER
            2 -> SABOTEUR_GOLD_PER_2_PLAYERS
            3 -> SABOTEUR_GOLD_PER_3_PLAYERS
            4 -> SABOTEUR_GOLD_PER_4_PLAYERS
            else -> 0
        }

        val distributed = mutableMapOf<String, List<GoldCard>>()

        saboteurs.forEach { playerId ->
            val gold = drawGoldCards(internal, goldPerSaboteur)
            if (gold.isNotEmpty()) {
                addGoldToPlayer(lobbyCode, playerId, gold)
                distributed[playerId] = gold
            }
        }

        return distributed
    }

    private fun buildPlayerGoldTotals(lobbyCode: String): Map<String, Int> {
        return gameService.getAllPlayerData(lobbyCode).mapValues { (_, player) ->
            player.goldCards.sumOf { it.value }
        }
    }

    private fun buildRevealedRoles(lobbyCode: String): Map<String, Role> {
        return gameService.getAllPlayerData(lobbyCode)
            .mapNotNull { (playerId, player) ->
                player.role?.let { role -> playerId to role }
            }
            .toMap()
    }

    private fun determineNextRoundStartPlayer(internal: GameInternalState): String? {
        val playersSorted = internal.gameState.players.sortedBy { it.turnOrder }
        if (playersSorted.isEmpty()) return null

        val lastPlayerId = internal.lastPlayerWhoPlayed
        if (lastPlayerId == null) return playersSorted.firstOrNull()?.playerId

        val lastIndex = playersSorted.indexOfFirst { it.playerId == lastPlayerId }
        if (lastIndex == -1) return playersSorted.firstOrNull()?.playerId

        return playersSorted[(lastIndex + 1) % playersSorted.size].playerId
    }

    private fun resetPlayersForNextRound(players: List<PlayerTurn>): List<PlayerTurn> {
        return players.map { it.copy(blockedTools = emptySet()) }
    }

    private fun createNextRoundGameState(
        previousState: GameState,
        newPlayers: List<PlayerTurn>,
        startPlayerId: String,
        newBoardPlacements: List<PlacedTunnelCard>,
        newDeckSize: Int
    ): GameState {
        return previousState.copy(
            players = newPlayers,
            currentPlayerId = startPlayerId,
            boardPlacements = newBoardPlacements,
            deckSize = newDeckSize,
            currentRound = previousState.currentRound + 1,
            isRoundOver = false,
            isGameOver = false,
            lastRoundResult = previousState.lastRoundResult
        )
    }

    private fun startNextRound(lobbyCode: String, internal: GameInternalState) {
        val playerIds = internal.gameState.players.map { it.playerId }
        val distribution = CardDistributor.distribute(playerIds)

        val startPlayerId = determineNextRoundStartPlayer(internal)
            ?: throw IllegalStateException("Kein Startspieler für nächste Runde gefunden")

        val resetPlayers = resetPlayersForNextRound(internal.gameState.players)
        val goalCards = CardDeck.createGoalCards().shuffled()
        val newBoardPlacements = listOf(
            PlacedTunnelCard(position = GOAL_POSITION_1, card = goalCards[0]),
            PlacedTunnelCard(position = GOAL_POSITION_2, card = goalCards[1]),
            PlacedTunnelCard(position = GOAL_POSITION_3, card = goalCards[2]),
            PlacedTunnelCard(position = START_POSITION, card = CardDeck.createStartCard())
        )

        internal.hands = distribution.hands.mapValues { it.value.toMutableList() }
        internal.drawPile = distribution.drawPile.toMutableList()
        internal.discardPile = mutableListOf()
        internal.deckWasEmptied = false
        internal.passedSinceEmpty = 0
        internal.knownGoalsByPlayer = playerIds.associateWith {
            mutableMapOf<BoardPosition, TunnelCard>()
        }.toMutableMap()
        internal.lastPlayerWhoPlayed = null

        internal.gameState = createNextRoundGameState(
            previousState = internal.gameState,
            newPlayers = resetPlayers,
            startPlayerId = startPlayerId,
            newBoardPlacements = newBoardPlacements,
            newDeckSize = distribution.drawPile.size
        )

        updatePlayerGoldValues(lobbyCode, internal)
    }

    private fun nextPlayerId(state: GameState): String? {
        val sorted = state.players.sortedBy { it.turnOrder }
        val idx = sorted.indexOfFirst { it.playerId == state.currentPlayerId }
        return if (idx == -1) sorted.firstOrNull()?.playerId else sorted[(idx + 1) % sorted.size].playerId
    }

    private fun buildGrid(placements: List<PlacedTunnelCard>) = placements.associateBy { it.position }

    private fun canPlaceOnBoard(p: BoardPosition, c: TunnelCard, pl: List<PlacedTunnelCard>): Boolean {
        if (pl.any { it.position == p }) return false
        val grid = buildGrid(pl)
        val neighbors = Direction.entries.map { it to grid[boardNeighbor(p, it)] }
        if (neighbors.all { it.second == null }) return false
        return neighbors.all { (dir, n) ->
            if (n == null || (n.card.type == CardType.GOAL && !n.card.isRevealed)) true
            else (dir in c.connections) == (opposite(dir) in n.card.connections)
        } && isReachableFromStart(p, grid)
    }

    private fun isReachableFromStart(p: BoardPosition, grid: Map<BoardPosition, PlacedTunnelCard>): Boolean {
        val visited = bfsVisited(grid)
        return Direction.entries.any { dir ->
            val n = boardNeighbor(p, dir)
            n in visited && opposite(dir) in (grid[n]?.card?.connections ?: emptySet())
        }
    }

    private fun isGoalReached(grid: Map<BoardPosition, PlacedTunnelCard>): Boolean {
        val goals = grid.values.filter { it.card.type == CardType.GOAL && it.card.isRevealed && it.card.isGoal }
        if (goals.isEmpty()) return false
        val visited = bfsVisited(grid)
        return goals.any { it.position in visited }
    }

    private fun bfsVisited(grid: Map<BoardPosition, PlacedTunnelCard>): Set<BoardPosition> {
        val startPos = grid.entries.find { it.value.card.type == CardType.START }?.key ?: return emptySet()
        val visited = mutableSetOf(startPos)
        val queue = ArrayDeque(listOf(startPos))
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            val currCard = grid[curr]?.card ?: continue
            for (dir in Direction.entries) {
                val next = boardNeighbor(curr, dir)
                if (next in visited) continue
                val nextCard = grid[next]?.card ?: continue
                if (!(nextCard.type == CardType.PATH || nextCard.type == CardType.START || (nextCard.type == CardType.GOAL && nextCard.isRevealed))) continue
                if (dir in currCard.connections && opposite(dir) in nextCard.connections) {
                    visited.add(next)
                    queue.add(next)
                }
            }
        }
        return visited
    }

    private fun boardNeighbor(pos: BoardPosition, dir: Direction) = when (dir) {
        Direction.TOP -> BoardPosition(pos.row - 1, pos.column)
        Direction.BOTTOM -> BoardPosition(pos.row + 1, pos.column)
        Direction.LEFT -> BoardPosition(pos.row, pos.column - 1)
        Direction.RIGHT -> BoardPosition(pos.row, pos.column + 1)
    }

    private fun revealGoalCards(p: BoardPosition, c: TunnelCard, pl: List<PlacedTunnelCard>): List<PlacedTunnelCard> {
        val grid = pl.associateBy { it.position }.toMutableMap()
        for (dir in Direction.entries) {
            val nPos = boardNeighbor(p, dir)
            val nPl = grid[nPos] ?: continue
            if (nPl.card.type != CardType.GOAL || nPl.card.isRevealed || dir !in c.connections) continue
            val goalConnects = opposite(dir) in nPl.card.connections
            grid[nPos] = nPl.copy(card = if (!goalConnects) nPl.card.copy(connections = nPl.card.connections.map { opposite(it) }.toSet(), isRotated = true, isRevealed = true) else nPl.card.copy(isRevealed = true))
        }
        return pl.map { grid.getValue(it.position) }
    }

    private fun determineWinner(lobbyCode: String, state: GameState, internal: GameInternalState): String? {


        val golddiggerWin = isGoalReached(buildGrid(state.boardPlacements))
        val saboteurWin = internal.deckWasEmptied && internal.passedSinceEmpty >= state.players.size

        if (!golddiggerWin && !saboteurWin) return null

        val winnerRole = if (golddiggerWin) Role.GOLDDIGGER else Role.SABOTEUR
        val winningPlayerIds = if (winnerRole == Role.GOLDDIGGER) {
            getPlayersByRole(lobbyCode, Role.GOLDDIGGER).map { it.id }
        } else {
            getPlayersByRole(lobbyCode, Role.SABOTEUR).map { it.id }
        }

        val distributedGold = if (winnerRole == Role.GOLDDIGGER) {
            val winningPlayerId = internal.lastPlayerWhoPlayed
                ?: state.currentPlayerId
                ?: winningPlayerIds.firstOrNull()
                ?: ""
            distributeGoldToGolddiggers(lobbyCode, internal, winningPlayerId)
        } else {
            distributeGoldToSaboteurs(lobbyCode, internal)
        }

        updatePlayerGoldValues(lobbyCode, internal)

        val isGameFinished = state.currentRound >= MAX_ROUNDS
        val playerGoldTotals = buildPlayerGoldTotals(lobbyCode)

        val finalWinnerIds = if (isGameFinished) {
            val maxGold = playerGoldTotals.values.maxOrNull() ?: 0
            playerGoldTotals
                .filterValues { it == maxGold }
                .keys
                .toList()
        } else {
            emptyList()
        }

        val roundResult = RoundResult(
            roundNumber = state.currentRound,
            winnerRole = winnerRole,
            winningPlayerIds = winningPlayerIds,
            revealedRoles = buildRevealedRoles(lobbyCode),
            distributedGold = distributedGold,
            playerGoldTotals = playerGoldTotals,
            gameFinished = isGameFinished,
            finalWinnerIds = finalWinnerIds
        )

        internal.gameState = internal.gameState.copy(
            players = internal.gameState.players.map { playerTurn ->
                playerTurn.copy(
                    goldValue = playerGoldTotals[playerTurn.playerId] ?: playerTurn.goldValue
                )
            },
            isRoundOver = true,
            isGameOver = isGameFinished,
            lastRoundResult = roundResult
        )

        if (!isGameFinished) {
            startNextRound(lobbyCode, internal)
        } else {
            finalizeAndPersist(lobbyCode, internal)
        }

        return if (winnerRole == Role.GOLDDIGGER) "DWARVES" else "SABOTEURS"
    }

    private fun opposite(d: Direction) = when (d) {
        Direction.TOP -> Direction.BOTTOM
        Direction.BOTTOM -> Direction.TOP
        Direction.LEFT -> Direction.RIGHT
        Direction.RIGHT -> Direction.LEFT
    }

    fun removeGame(code: String) {
        games.remove(code)
    }
}
