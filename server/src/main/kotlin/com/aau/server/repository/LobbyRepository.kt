package com.aau.server.repository

import com.aau.server.model.LobbyEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LobbyRepository : JpaRepository<LobbyEntity, String>
