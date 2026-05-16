package com.aau.server

import com.aau.saboteur.model.Player
import com.aau.server.repository.GameRepository
import com.aau.server.repository.LobbyRepository
import com.aau.server.service.GameLifecycleService
import com.aau.server.service.LobbyService
import com.aau.server.service.TurnManager
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PersistenceRecoveryIntegrationTest {

    @Autowired lateinit var lobbyService: LobbyService
    @Autowired lateinit var turnManager: TurnManager
    @Autowired lateinit var gameLifecycleService: GameLifecycleService
    @Autowired lateinit var lobbyRepository: LobbyRepository
    @Autowired lateinit var gameRepository: GameRepository
    @Autowired lateinit var entityManager: EntityManager

    @Test
    @Transactional
    fun `Game state survives full server restart simulation`() {
        val lobbyCode = "TEST1"
        val players = listOf(
            Player("p1", "Alice"),
            Player("p2", "Bob"),
            Player("p3", "Charlie")
        )

        // 1. Create lobby and start game
        val lobby = lobbyService.createLobby("Alice", "p1")
        val code = lobby.lobbyCode
        lobbyService.joinLobby(code, "Bob", "p2")
        lobbyService.joinLobby(code, "Charlie", "p3")
        gameLifecycleService.startGame(code, "p1", players)

        // 2. Take a turn
        val stateBefore = turnManager.getGameState(code)
        val currentPlayerId = stateBefore.currentPlayerId!!
        val hand = turnManager.getHands(code)[currentPlayerId]!!
        val cardToPlay = hand.first()
        turnManager.discardCard(code, currentPlayerId, cardToPlay.id)

        // WICHTIG: Flush, damit alles wirklich in die DB geschrieben ist
        entityManager.flush()
        gameRepository.flush()
        lobbyRepository.flush()
        entityManager.clear()

        val stateAfterMove = turnManager.getGameState(code)
        assertNotEquals(currentPlayerId, stateAfterMove.currentPlayerId, "Turn should have advanced")

        // 3. Simulate restart: clear RAM caches
        turnManager.removeGame(code)

        // Debug: How many games are in the DB?
        println("Games in DB: ${gameRepository.findAll().joinToString { it.lobbyCode }}")
        assertTrue(gameRepository.existsById(code), "Game should exist in DB after flush!!")

        // 4. Reload from DB
        lobbyService.loadFromDb()
        turnManager.loadFromDb()

        // Debug: Is the game present now?
        println(
            "TurnManager knows game? " +
                    try { turnManager.getGameState(code); true } catch (_: Exception) { false }
        )

        // 5. Verification, catch for better fail message
        val recoveredState = try {
            turnManager.getGameState(code)
        } catch (e: IllegalArgumentException) {
            fail("Game with code $code was not found in TurnManager after loadFromDb. Check loadFromDb().")
        }
        val recoveredLobby = lobbyService.getLobby(code)

        assertEquals(stateAfterMove.currentPlayerId, recoveredState.currentPlayerId, "Recovered turn must match")
        assertEquals(stateAfterMove.boardPlacements.size, recoveredState.boardPlacements.size, "Board must match")
        assertTrue(recoveredLobby.gameStarted, "Lobby must still be in 'started' state")

        val recoveredHand = turnManager.getHands(code)[currentPlayerId]!!
        assertFalse(recoveredHand.any { it.id == cardToPlay.id }, "Played card must remain gone after recovery")
    }
}