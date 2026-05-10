package com.aau.server.repository

import com.aau.server.model.GameSessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GameSessionRepository : JpaRepository<GameSessionEntity, String>
