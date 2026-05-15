# State Ownership & Data Flow

Dieses Dokument definiert, welche Komponente im Saboteur-System für welche Daten autoritativ ist.

## 1. Backend (Server)

Der Server hält den "Master State" in drei spezialisierten Services:

### LobbyService
- **Verantwortung:** Teilnehmerliste, Host-Status, Lobby-Code, Spielstatus (Lobby/Game).
- **Persistenz:** `LobbyRepository` (H2 Datenbank).
- **Events:** `LOBBY_STATE_UPDATE`, `LOBBY_LIST_UPDATE`.

### GameService
- **Verantwortung:** Zuweisung von Rollen (Zwerg/Saboteur) und initiale Spieldaten.
- **Persistenz:** `GameRepository` (Mapping PlayerId -> Role).
- **Events:** `PLAYER_DATA` (individuell pro Spieler).

### TurnManager
- **Verantwortung:** Spielfeld (Board), Handkarten der Spieler, Nachziehstapel, aktueller Zug.
- **Persistenz:** `GameRepository` (Serialisiertes Board & Hands JSON).
- **Mechanik:** Validiert jeden Spielzug gegen den aktuellen RAM-Cache und persistiert danach.
- **Events:** `GAME_STATE_UPDATE`, `CARDS_DEALT`, `VALID_POSITIONS`.

## 2. Client (Android)

### LobbyViewModel
- Verwaltet den `LobbyState` und die Identität des lokalen Nutzers.
- Initiiert den Reconnect-Prozess beim App-Start.
- **Zustand:** Synchronisiert sich primär über `LOBBY_STATE_UPDATE`.

### GameViewModel
- Hält den UI-State für das aktive Spiel (`GameUiState`).
- Verarbeitet Züge und steuert das Sync-Overlay.
- **Synchronisation:** Nutzt `GameApi` als Datenquelle, welches Events von WebSockets und HTTP-Responses (Reconnect) zusammenführt.

## 3. Datenfluss bei Reconnect

1. **HTTP GET/POST:** Der Client holt sich beim Reconnect ein "Initialpaket" (Lobby + Game + Hand).
2. **WebSocket Snapshot:** Nach dem `REGISTER` sendet der Server einen Snapshot, der den HTTP-Stand überschreibt/aktualisiert.
3. **Live-Events:** Alle weiteren Züge fließen rein reaktiv über den WebSocket.
