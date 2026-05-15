# Session & WebSocket Architektur

Dieses Dokument beschreibt, wie Saboteur Identitäten verwaltet und wie die Kommunikation zwischen Client und Server über WebSockets und REST synchronisiert wird.

## 1. Identität & PlayerId

Jeder Spieler wird eindeutig über eine `playerId` (UUID String) identifiziert.

### Gäste
- Beim ersten `createLobby` oder `joinLobby` generiert der Server eine zufällige ID, falls keine mitgesendet wird.
- Diese ID wird in den Android `SharedPreferences` gespeichert.
- Sie bleibt über App-Neustarts hinweg erhalten, solange der Nutzer die Lobby nicht verlässt oder sich neu anmeldet.

### Registrierte Benutzer
- Die `playerId` ist fest im `UserEntity` in der Datenbank gespeichert.
- Nach dem Login wird diese ID an den Client gesendet und überschreibt dort jegliche Gast-Identität.
- Dies ermöglicht es, sich auf einem anderen Gerät einzuloggen und eine laufende Session fortzuführen.

## 2. Session Lifecycle

### Start & Auto-Reconnect
Beim Start der App prüft das `LobbyViewModel`:
1. Sind `playerId` und `lobbyCode` gespeichert?
2. Falls ja: Sende `POST /api/lobby/reconnect`.
3. Falls erfolgreich: Öffne WebSocket und sende `REGISTER(reconnect=true)`.
4. Falls fehlgeschlagen (404 Lobby weg): Lösche lokale Session-Daten stillschweigend.

### Expliziter Logout / Lobby verlassen
- Ruft `LobbyApi.leaveLobby` auf.
- Löscht `lobbyCode` aus den SharedPreferences.
- Schließt den WebSocket.
- Setzt die UI zurück.

## 3. WebSocket Synchronisation (The Buffer System)

Um zu verhindern, dass Nachrichten während eines Verbindungsabbruchs verloren gehen, nutzt der Server ein Buffering-System:

1. **Disconnected:** Wenn ein Spieler die Verbindung verliert, merkt sich der `MessagingService` die `playerId`.
2. **Buffering:** Alle Events für diesen Spieler werden in einer Queue gespeichert.
3. **Register:** Nach dem Wiederverbinden sendet der Client `REGISTER`.
4. **Snapshot:** Der Server sendet ein `RECONNECT_SNAPSHOT` mit dem absolut aktuellen Stand (Board, Handkarten, Turn).
5. **Sync Ack:** Der Client verarbeitet den Snapshot und sendet `SYNC_ACK`.
6. **Flush:** Erst jetzt sendet der Server alle gepufferten Events, die seit dem Snapshot-Zeitpunkt aufgelaufen sind.
7. **Complete:** Das Event `SYNC_COMPLETE` signalisiert dem Client, dass er nun wieder "live" ist.

## 4. Sicherheitsmechanismen

- **Navigation Guard:** Der `ActiveLobbyScreen` navigiert nur zum `GameScreen`, wenn `gameStarted == true` UND die eigene `playerId` in der Spielerliste der Lobby existiert.
- **WebSocket Reset:** Vor jedem neuen Verbindungsaufbau (Login/Join) wird `WebSocketManager.disconnect()` aufgerufen, um sicherzustellen, dass keine alten Listener oder Sockets aktiv sind.
- **HTTP/WS Sync:** Daten, die via HTTP (Join/Reconnect Response) empfangen werden, werden sofort in das `GameApi` Singleton injiziert, damit sie beim Screen-Wechsel sofort verfügbar sind.
