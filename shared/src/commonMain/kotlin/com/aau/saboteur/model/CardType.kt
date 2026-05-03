package com.aau.saboteur.model

import kotlinx.serialization.Serializable

@Serializable
enum class CardType {

    PATH,
    DEAD_END,
    START,
    GOAL,

    // Werkzeuge blockieren/sperren (red)
    CART_RED,
    LANTERN_RED,
    PICKAXE_RED,
    TRACK_RED,

    // Werkzeuge reparieren/entsperren (green)
    CART_GREEN,
    LANTERN_GREEN,
    PICKAXE_GREEN,
    TRACK_GREEN,

    // Mehrfach-Reparaturkarten ("double" immer grün/entsperren)
    DOUBLE_LANTERN_CART,
    DOUBLE_PICKAXE_CART,
    DOUBLE_PICKAXE_LANTERN
}