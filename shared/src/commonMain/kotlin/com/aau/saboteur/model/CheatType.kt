package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
enum class CheatType {
    LANTERN_FLASHLIGHT,
    TEST_REVEAL,      // Nur für interne Tests / Zukünftige Features (verbraucht einen Zug)
    TEST_INVALID_TYPE // Nur für Coverage des 'else' Pfads
}
