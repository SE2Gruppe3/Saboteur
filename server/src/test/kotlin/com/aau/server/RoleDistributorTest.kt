package com.aau.server

import com.aau.saboteur.model.Role
import com.aau.server.service.RoleDistributor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RoleDistributorTest {

    @Test
    fun `distributeRoles throws exception for invalid player counts`() {
        assertThrows<IllegalArgumentException> { RoleDistributor.distributeRoles(createPlayerIds(2)) }
        assertThrows<IllegalArgumentException> { RoleDistributor.distributeRoles(createPlayerIds(11)) }
    }

    @Test
    fun `distributeRoles assigns one role per player`() {
        val playerIds = createPlayerIds(5)
        val roles = RoleDistributor.distributeRoles(playerIds)
        
        assertEquals(5, roles.size)
        playerIds.forEach { id ->
            assertTrue(roles.containsKey(id))
            assertNotNull(roles[id])
        }
    }

    @Test
    fun `distributeRoles follows official rules for role counts`() {
        // According to rules, pool size is N+1. 
        // One card is left out, so assigned count is either poolCount or poolCount - 1.
        val testCases = mapOf(
            3 to (0..1 to 2..3),  // Pool: 1S, 3G
            4 to (0..1 to 3..4),  // Pool: 1S, 4G
            5 to (1..2 to 3..4),  // Pool: 2S, 4G
            6 to (1..2 to 4..5),  // Pool: 2S, 5G
            7 to (2..3 to 4..5),  // Pool: 3S, 5G
            8 to (2..3 to 5..6),  // Pool: 3S, 6G
            9 to (2..3 to 6..7),  // Pool: 3S, 7G
            10 to (3..4 to 6..7)  // Pool: 4S, 7G
        )

        testCases.forEach { (count, ranges) ->
            val (sabRange, goldRange) = ranges
            val roles = RoleDistributor.distributeRoles(createPlayerIds(count)).values
            
            val sabCount = roles.count { it == Role.SABOTEUR }
            val goldCount = roles.count { it == Role.GOLDDIGGER }
            
            assertTrue(sabCount in sabRange, "Player count $count: Expected saboteurs in $sabRange but got $sabCount")
            assertTrue(goldCount in goldRange, "Player count $count: Expected goldsmiths in $goldRange but got $goldCount")
            assertEquals(count, sabCount + goldCount)
        }
    }

    private fun createPlayerIds(count: Int): List<String> = (1..count).map { "player$it" }
}
