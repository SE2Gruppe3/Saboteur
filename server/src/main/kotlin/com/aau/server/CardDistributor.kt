package com.aau.server

object CardDistributor {

    /**
     * Distributes cards to players at game start following official Saboteur rules.
     *
     * @param playerIds ordered list of player IDs (3–10 players)
     * @return [CardDistributionResult] with hands, draw pile, goal cards, and start card
     * @throws IllegalArgumentException if player count is outside 3–10
     */
    fun distribute(playerIds: List<String>): CardDistributionResult {
        val playerCount = playerIds.size
        require(playerCount in 3..10) {
            "Player count must be between 3 and 10, was $playerCount"
        }

        val cardsPerPlayer = when {
            playerCount <= 5 -> 6
            playerCount <= 7 -> 5
            else -> 4
        }
        val shuffledDeck = CardDeck.shuffled(CardDeck.createTunnelDeck())

        val hands = playerIds.mapIndexed { index, playerId ->
            val start = index * cardsPerPlayer
            playerId to shuffledDeck.subList(start, start + cardsPerPlayer)
        }.toMap()

        val drawPile = shuffledDeck.drop(playerCount * cardsPerPlayer)

        return CardDistributionResult(
            hands = hands,
            drawPile = drawPile,
            goalCards = CardDeck.createGoalCards(),
            startCard = CardDeck.createStartCard()
        )
    }
}