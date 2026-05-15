# Testing Strategy - Phase 2.2

## Übersicht
Die Robustheit des Reconnects und der Synchronisation wird durch eine Kombination aus Unit-Tests, Integration-Tests und manuellen Szenarien abgesichert.

## Test-Kategorien

### 1. Reconnect & Snapshot Tests
- **Szenario**: App-Kill während eines Spielzugs.
- **Erwartung**: Nach Neustart muss `/reconnect` (REST) die Identität liefern und `REGISTER` (WS) den vollständigen Board-State via `ReconnectSnapshot` wiederherstellen.
- **Validierung**: `RegisterHandlerTest` prüft die Snapshot-Generierung.

### 2. Lobby & Cleanup Tests
- **Szenario**: Spieler verlässt die App ohne "Leave Lobby" zu drücken.
- **Erwartung**: Nach 3 Minuten Inaktivität (kein Heartbeat + keine Session) wird die Lobby automatisch gelöscht.
- **Validierung**: `LobbyServiceCleanupTest` (Mocking von System.currentTimeMillis).

### 3. Concurrency & Locking Tests
- **Szenario**: Zwei Spieler versuchen gleichzeitig Karten zu legen oder zu beitreten.
- **Erwartung**: Der `LobbyLock` (ReentrantLock) im `MessagingService` erzwingt die sequentielle Abarbeitung.
- **Validierung**: Multi-threaded Tests im `TurnManager` oder `MessagingService`.

### 4. Guest Identity Persistence
- **Szenario**: Erster Start der App -> Gast-Login -> App schließen -> Zweiter Start.
- **Erwartung**: Die `playerId` in den `SharedPreferences` muss identisch bleiben.
- **Validierung**: `SessionRepositoryTest`.

## Test Matrix (Abdeckung)
| Feature | Test Klasse | Status |
| :--- | :--- | :--- |
| Snapshot Protocol | `RegisterHandlerTest` | ✅ |
| Lobby Cleanup | `LobbyServiceCleanupTest` | ✅ |
| Event Buffering | `MessagingServiceBufferTest` | ✅ |
| Guest Auth | `AuthControllerTest` | ✅ |
| H2 Persistence | `TurnManagerDbTest` | ✅ |
