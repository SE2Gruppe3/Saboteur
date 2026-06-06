package com.aau.saboteur.model

import kotlinx.serialization.Serializable

/**
 * Repräsentiert eine Goldkarte mit eindeutigem Wert.
 *
 * Werte im Standardset: 16 Karten à Wert 1, 8 Karten à Wert 2, 4 Karten à Wert 3.
 * Goldkarten werden nach Rundenende an Gewinner verteilt.
 */

@Serializable
data class GoldCard(
    val id: String,
    val value: Int
)
