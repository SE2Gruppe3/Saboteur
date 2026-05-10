package com.aau.server.service

import com.aau.saboteur.model.GameState
import com.aau.server.repository.GameSessionRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SessionPersistenceTest {

    @Autowired
    private lateinit var sessionService: SessionService

    @Autowired
    private lateinit var repository: GameSessionRepository

    @Test
    fun `session should persist in database and be reloadable`() {
        // 1. Create a session
        val originalSession = sessionService.createSession("HostPlayer")
        val sessionId = originalSession.sessionId
        
        // 2. Add some game state
        val gameState = GameState(currentPlayerId = "player-1")
        sessionService.updateGameState(sessionId, gameState)
        
        // 3. Verify it's in DB
        assertTrue(repository.existsById(sessionId))
        
        // 4. Simulate reload by creating a fresh service instance
        val freshService = SessionService(repository)
        freshService.loadSessionsFromDb()
        
        val reloadedSession = freshService.getSession(sessionId)
        assertNotNull(reloadedSession)
        assertEquals(sessionId, reloadedSession?.sessionId)
        assertEquals(1, reloadedSession?.players?.size)
        assertEquals("HostPlayer", reloadedSession?.players?.first()?.name)
        assertEquals("player-1", reloadedSession?.gameState?.currentPlayerId)
    }
}
