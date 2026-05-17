package com.aau.server

import com.aau.saboteur.model.BoardPosition
import com.aau.saboteur.model.Player
import com.aau.server.repository.GameRepository
import com.aau.server.repository.LobbyRepository
import com.aau.server.service.GameLifecycleService
import com.aau.server.service.LobbyService
import com.aau.server.service.TurnManager
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

    @Test
    fun `Game state survives full server restart simulation`() {
        val lobbyCode = "TEST1"
        val players = listOf(
            Player("p1", "Alice"),
            Player("p2", "Bob"),
            Player("p3", "Charlie")
        )

        // 1. Spiel initialisieren und starten
        val lobby = lobbyService.createLobby("Alice", "p1")
        val code = lobby.lobbyCode
        lobbyService.joinLobby(code, "Bob", "p2")
        lobbyService.joinLobby(code, "Charlie", "p3")
        
        gameLifecycleService.startGame(code, "p1", players)

        // 2. Einen Zug machen
        val stateBefore = turnManager.getGameState(code)
        val currentPlayerId = stateBefore.currentPlayerId!!
        val hand = turnManager.getHands(code)[currentPlayerId]!!
        val cardToPlay = hand.first()
        
        // Wir werfen eine Karte ab, um den State zu ändern
        turnManager.discardCard(code, currentPlayerId, cardToPlay.id)
        
        val stateAfterMove = turnManager.getGameState(code)
        assertNotEquals(currentPlayerId, stateAfterMove.currentPlayerId, "Turn should have advanced")

        // 3. RESTART SIMULATION: Wir löschen die RAM-Caches, behalten aber die DB
        // In einem echten Szenario würde Spring neu starten und loadFromDb() rufen.
        // Wir triggern loadFromDb manuell auf den frischen Repositories.
        
        turnManager.removeGame(code) // RAM löschen
        
        // Recovery triggern
        lobbyService.loadFromDb()
        turnManager.loadFromDb()

        // 4. Verifikation
        val recoveredState = turnManager.getGameState(code)
        val recoveredLobby = lobbyService.getLobby(code)

        assertEquals(stateAfterMove.currentPlayerId, recoveredState.currentPlayerId, "Recovered turn must match")
        assertEquals(stateAfterMove.boardPlacements.size, recoveredState.boardPlacements.size, "Board must match")
        assertTrue(recoveredLobby.gameStarted, "Lobby must still be in 'started' state")
        
        val recoveredHand = turnManager.getHands(code)[currentPlayerId]!!
        assertFalse(recoveredHand.any { it.id == cardToPlay.id }, "Played card must remain gone after recovery")
    }
}
