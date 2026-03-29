package com.aau.saboteur.model

data class Player(
    val id: String,
    val name: String,
    val hand: MutableList<TunnelCard> = mutableListOf()
) {
    fun addCard(card: TunnelCard) { hand.add(card) }
    fun removeCard(card: TunnelCard) { hand.remove(card) }
}
