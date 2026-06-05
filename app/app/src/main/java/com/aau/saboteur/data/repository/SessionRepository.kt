package com.aau.saboteur.data.repository

import android.content.Context
import android.content.SharedPreferences

class SessionRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("saboteur_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PLAYER_ID = "player_id"
        private const val KEY_LOBBY_CODE = "lobby_code"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_IS_GUEST = "is_guest"
    }

    fun saveIdentity(playerId: String, userName: String, isGuest: Boolean = false) {
        prefs.edit().apply {
            putString(KEY_PLAYER_ID, playerId)
            putString(KEY_USER_NAME, userName)
            putBoolean(KEY_IS_GUEST, isGuest)
            apply()
        }
    }

    fun saveLobby(lobbyCode: String) {
        prefs.edit().putString(KEY_LOBBY_CODE, lobbyCode).apply()
    }

    fun saveSession(playerId: String, lobbyCode: String, userName: String, isGuest: Boolean = true) {
        prefs.edit().apply {
            putString(KEY_PLAYER_ID, playerId)
            putString(KEY_LOBBY_CODE, lobbyCode)
            putString(KEY_USER_NAME, userName)
            putBoolean(KEY_IS_GUEST, isGuest)
            apply()
        }
    }

    fun getPlayerId(): String? = prefs.getString(KEY_PLAYER_ID, null)
    fun getLobbyCode(): String? = prefs.getString(KEY_LOBBY_CODE, null)
    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)
    fun isGuest(): Boolean = prefs.getBoolean(KEY_IS_GUEST, true)

    fun clearLobby() {
        prefs.edit().remove(KEY_LOBBY_CODE).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
