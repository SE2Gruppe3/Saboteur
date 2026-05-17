package com.aau.server.health

import com.aau.server.service.LobbyService
import com.aau.server.service.MessagingService
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component("gameSystem")
class GameSystemHealthIndicator(
    private val lobbyService: LobbyService,
    private val messagingService: MessagingService
) : HealthIndicator {

    override fun health(): Health {
        val activeLobbies = lobbyService.getActiveLobbiesCount()
        val activeSessions = messagingService.getActiveSessionsCount()
        val registeredPlayers = messagingService.getRegisteredPlayersCount()

        // logic: if there are sessions but no lobbies (or vice versa in extreme cases), 
        // it's still "UP" but we could flag warnings if resource limits are reached.
        val healthBuilder = Health.up()
            .withDetail("activeLobbies", activeLobbies)
            .withDetail("connectedWebSockets", activeSessions)
            .withDetail("registeredPlayersInSessions", registeredPlayers)

        // Example threshold check
        if (activeLobbies > 500) {
            return healthBuilder.status("Slightly Overloaded").build()
        }

        return healthBuilder.build()
    }
}
