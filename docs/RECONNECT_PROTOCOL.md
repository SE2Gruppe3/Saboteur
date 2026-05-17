# Reconnect Protocol

Das Reconnect-Protokoll garantiert eine konsistente Wiederherstellung des Spielzustands nach Verbindungsabbrüchen.

## Ablauf (Sequence)

### 1. HTTP Handshake (Handshake-Phase)
Sobald die App startet oder ein Reconnect nötig wird:
- Client sendet `POST /api/lobby/reconnect { playerId, lobbyCode }`.
- Server validiert die Session und liefert eine `ReconnectResponse` zurück.
- **Wichtig:** Diese Antwort enthält bereits den aktuellen GameState und die Handkarten, damit die UI sofort (noch vor WebSocket-Verbindung) befüllt werden kann.

### 2. WebSocket Registrierung (Sync-Phase)
- Client öffnet WebSocket und sendet `REGISTER { playerId, lobbyCode, reconnect: true }`.
- Server sperrt die Lobby (Mutex) und beginnt, alle neuen Events für diesen Spieler zu puffern.
- Server sendet ein `RECONNECT_SNAPSHOT` Event (enthält den absolut aktuellen Stand zum Zeitpunkt der Registrierung).

### 3. Bestätigung & Live-Schaltung (Flush-Phase)
- Client empfängt Snapshot, aktualisiert seine lokalen Models und sendet `SYNC_ACK`.
- Server empfängt `SYNC_ACK` und leert den Event-Puffer (sendet alle verpassten Nachrichten).
- Server sendet `SYNC_COMPLETE`.
- Client blendet das "Reconnecting..." Overlay aus.

## Datenstrukturen

### ReconnectSnapshot
```kotlin
data class ReconnectSnapshot(
    val lobbyState: LobbyState,
    val gameState: GameState?,
    val playerState: Player, // Enthält Handkarten und Rolle
    val serverTimestamp: Long
)
```

## Fehlerbehandlung
- **404 (Lobby Not Found):** Die Lobby wurde serverseitig gelöscht (z.B. Timeout oder Spielende). Client löscht lokale Session-Daten.
- **403 (Forbidden):** Die PlayerId gehört nicht (mehr) zu dieser Lobby. Session wird bereinigt.
- **WS Failure:** Automatischer Retry-Mechanismus im `WebSocketManager` mit Exponential Backoff.
