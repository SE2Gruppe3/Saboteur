# Saboteur Session & Game State Recovery Guide

This guide explains how to use the session management system and how the Game State Recovery works.

## 1. Creating a New Session
A player can create a session by calling `SessionApi.createSession(playerName, context)`.
- **Server-side**: A unique 6-character ID is generated. The host player is created and saved. The session is persisted in the H2 database.
- **Client-side**: The `sessionId` and the generated `playerId` are automatically stored in Android's `SharedPreferences`.

## 2. Joining a Session
Use `SessionApi.joinSession(sessionId, playerName, context)`.
- Only possible if the game has not started yet.
- Credentials are also stored in `SharedPreferences` upon success.

## 3. Game State Recovery (Reconnect)
If the app crashes or the player loses connection:
1. The app calls `SessionApi.reconnect(context)` on startup.
2. The logic reads `last_session_id` and `my_player_id` from `SharedPreferences`.
3. A request is sent to the server.
4. **Server-side recovery**: The server checks if the session exists in its memory (or reloads it from H2 if the server was restarted). It validates the `playerId` and returns the full `SessionInfo` including the current `GameState`.

## 4. Port Configuration & `local.properties`
The connection URL is determined by the build type:
- **Debug**: Uses `BASE_URL_LOCAL` from your `local.properties` (e.g., `http://10.0.2.2:8080` for the emulator). If not set, it defaults to the emulator IP.
- **Release**: Uses the production URL `http://se2-demo.aau.at:53207`.

## 5. Technical Details
- **Persistence**: The server uses Spring Data JPA with an H2 file-based database (`./data/saboteur_db`).
- **Data Format**: Complex objects like `GameState` are stored as JSON strings (CLOB) in the database to allow flexible updates without complex schema changes.
- **Communication**: We use `kotlinx-serialization` for JSON handling on both Android and Server.
