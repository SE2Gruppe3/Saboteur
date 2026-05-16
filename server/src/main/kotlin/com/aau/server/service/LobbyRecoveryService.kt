package com.aau.server.service

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LobbyRecoveryService(
    private val lobbyService: LobbyService,
    private val turnManager: TurnManager
) {
    private val logger = LoggerFactory.getLogger(LobbyRecoveryService::class.java)

    @PostConstruct
    @Transactional(readOnly = true)
    fun recoverState() {
        logger.info("Starting Saboteur State Recovery...")
        
        // 1. Recover Lobbies
        val recoveredLobbies = lobbyService.loadFromDb()
        logger.info("Recovered {} lobbies", recoveredLobbies)

        // 2. Recover Active Games
        val recoveredGames = turnManager.loadFromDb()
        logger.info("Recovered {} active games", recoveredGames)
        
        logger.info("State recovery complete.")
    }
}
