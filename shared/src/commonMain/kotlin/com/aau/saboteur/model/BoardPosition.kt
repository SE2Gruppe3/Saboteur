package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
data class BoardPosition(
    val row: Int,
    val column: Int
) {
    override fun toString(): String = "$row,$column"
    companion object {
        fun fromString(s: String): BoardPosition {
            val (row, column) = s.split(",").map { it.toInt() }
            return BoardPosition(row, column)
        }
    }
}
