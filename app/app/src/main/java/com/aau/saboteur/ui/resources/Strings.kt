package com.aau.saboteur.ui.resources


interface AppStrings {
    val loginTitle: String
    val usernameLabel: String
    val passwordLabel: String
    val loginButton: String
    val guestJoinButton: String

}

object GermanStrings : AppStrings {
    override val loginTitle = "Saboteur Login"
    override val usernameLabel = "Zwergenname"
    override val passwordLabel = "Passwort"
    override val loginButton = "Anmelden"
    override val guestJoinButton = "Als Gast beitreten"

}