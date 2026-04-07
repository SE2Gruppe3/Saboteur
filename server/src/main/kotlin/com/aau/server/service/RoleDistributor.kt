package com.aau.server.service

import com.aau.saboteur.model.Role
object RoleDistributor {
    fun distributeRoles(playerIds: List<String>): Map<String, Role> {
        val playerCount = playerIds.size
        require(playerCount in 3..10) {
            "Player count must be between 3 and 10, was $playerCount"
        }

        val rolePool = getRolePool(playerCount).shuffled()

        return playerIds.indices.associate { i ->
            playerIds[i] to rolePool[i]
        }
    }
    private fun getRolePool(playerCount: Int): List<Role> {
        val (saboteurs, goldsmiths) = when (playerCount) {
            3 -> 1 to 3
            4 -> 1 to 4
            5 -> 2 to 4
            6 -> 2 to 5
            7 -> 3 to 5
            8 -> 3 to 6
            9 -> 3 to 7
            10 -> 4 to 7
            else -> throw IllegalArgumentException("Invalid player count: $playerCount")
        }

        return List(saboteurs) { Role.SABOTEUR } + List(goldsmiths) { Role.GOLDSMITH }
    }
}
