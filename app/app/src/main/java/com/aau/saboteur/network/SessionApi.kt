package com.aau.saboteur.network

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.aau.saboteur.model.JoinSessionRequest
import com.aau.saboteur.model.ReconnectRequest
import com.aau.saboteur.model.SessionInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * API client for managing game sessions and handling Game State Recovery.
 *
 * This object manages the lifecycle of a session on the client side, including:
 * - Creating a new session and becoming the host.
 * - Joining an existing session via a session ID.
 * - Reconnecting to a previous session using stored credentials in SharedPreferences.
 *
 * ### Game State Recovery Logic:
 * When a session is created or joined, the `sessionId` and the player's unique `playerId`
 * are stored in the Android [SharedPreferences]. If the app restarts or the connection is lost,
 * [reconnect] can be called to restore the session state from the server using these stored IDs.
 */
object SessionApi {
    private var dispatcher: CoroutineDispatcher = Dispatchers.IO
    private var scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val json = Json { ignoreUnknownKeys = true }

    private val _sessionUpdates = MutableSharedFlow<SessionInfo>(replay = 1, extraBufferCapacity = 10)
    /**
     * Flow emitting updates to the current [SessionInfo].
     */
    val sessionUpdates: SharedFlow<SessionInfo> = _sessionUpdates.asSharedFlow()

    private val _errorMessages = MutableSharedFlow<String?>(replay = 1, extraBufferCapacity = 10)
    /**
     * Flow emitting error messages or null if the last operation was successful.
     */
    val errorMessages: SharedFlow<String?> = _errorMessages.asSharedFlow()

    /**
     * Injects a dispatcher for testing purposes to ensure coroutines are predictable.
     */
    @VisibleForTesting
    fun setDispatcher(testDispatcher: CoroutineDispatcher) {
        dispatcher = testDispatcher
        scope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    /**
     * Resets the flows to clear any replayed values from previous tests.
     */
    @VisibleForTesting
    fun reset() {
        _errorMessages.tryEmit(null)
        // Note: we don't reset _sessionUpdates here to avoid side effects unless needed
    }

    /**
     * Creates a new game session on the server.
     *
     * @param playerName The name of the player creating the session.
     * @param context Android context used to save the session credentials to SharedPreferences.
     */
    fun createSession(playerName: String, context: Context) {
        scope.launch {
            try {
                val url = "${NetworkConstants.baseUrl}/api/sessions/create?playerName=$playerName"
                val req = Request.Builder().url(url).post("".toRequestBody()).build()

                HttpClient.okHttpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val session = json.decodeFromString<SessionInfo>(body)
                        saveSession(context, session.sessionId, session.players.first().id)
                        _sessionUpdates.emit(session)
                        _errorMessages.emit(null)
                    } else {
                        _errorMessages.emit("Create failed: ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                _errorMessages.emit("Error: ${e.message}")
            }
        }
    }

    /**
     * Joins an existing game session.
     *
     * @param sessionId The 6-character unique ID of the session to join.
     * @param playerName The name of the player joining the session.
     * @param context Android context used to save the session credentials.
     */
    fun joinSession(sessionId: String, playerName: String, context: Context) {
        scope.launch {
            try {
                val url = "${NetworkConstants.baseUrl}/api/sessions/join"
                val payload = json.encodeToString(JoinSessionRequest(sessionId, playerName))
                val req = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                HttpClient.okHttpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val session = json.decodeFromString<SessionInfo>(body)
                        val myPlayerId = session.players.find { it.name == playerName }?.id ?: ""
                        saveSession(context, session.sessionId, myPlayerId)
                        _sessionUpdates.emit(session)
                        _errorMessages.emit(null)
                    } else {
                        _errorMessages.emit("Join failed: ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                _errorMessages.emit("Error: ${e.message}")
            }
        }
    }

    /**
     * Attempts to reconnect to a previously active session using credentials from SharedPreferences.
     *
     * This is the core of the Game State Recovery. If the IDs are found, a request is sent
     * to the server to retrieve the current state of that session.
     *
     * @param context Android context to access SharedPreferences.
     */
    fun reconnect(context: Context) {
        val prefs = context.getSharedPreferences("saboteur_prefs", Context.MODE_PRIVATE)
        val sessionId = prefs.getString("last_session_id", null) ?: return
        val playerId = prefs.getString("my_player_id", null) ?: return

        scope.launch {
            try {
                val url = "${NetworkConstants.baseUrl}/api/sessions/reconnect"
                val payload = json.encodeToString(ReconnectRequest(playerId, sessionId))
                val req = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                HttpClient.okHttpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val session = json.decodeFromString<SessionInfo>(body)
                        _sessionUpdates.emit(session)
                        _errorMessages.emit(null)
                    } else {
                        _errorMessages.emit("Reconnect failed: ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                _errorMessages.emit("Reconnect error: ${e.message}")
            }
        }
    }

    /**
     * Stores the session and player IDs in SharedPreferences.
     */
    private fun saveSession(context: Context, sessionId: String, playerId: String) {
        context.getSharedPreferences("saboteur_prefs", Context.MODE_PRIVATE).edit()
            .putString("last_session_id", sessionId)
            .putString("my_player_id", playerId)
            .apply()
    }
}
