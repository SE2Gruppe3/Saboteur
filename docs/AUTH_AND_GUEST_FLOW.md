# Authentifizierung & Gast-Flow

## Identitäts-Konzept

In Saboteur gibt es zwei Arten von Spielern. Beide werden technisch über eine `playerId` identifiziert, unterscheiden sich aber in ihrer Persistenz.

### 1. Gast-Spieler (Ohne Passwort)
- **Erstellung:** Erhalten bei der ersten Aktion (`Lobby create/join`) eine UUID vom Server.
- **Speicherung:** Die `playerId` wird lokal im `SessionRepository` (SharedPreferences) gespeichert.
- **Session:** Bleibt über App-Neustarts erhalten. Ein Gast kann nach einem Crash seine Runde fortsetzen.
- **Löschung:** Sobald der Gast auf "Lobby verlassen" klickt oder sich ein registrierter User einloggt, wird die Gast-Identität gelöscht.

### 2. Registrierte Benutzer (Mit Passwort)
- **Identität:** Die `playerId` ist fest mit dem Account verknüpft.
- **Login:** Beim Login wird die `playerId` vom Server geliefert und lokal gespeichert.
- **Vorteil:** Registrierte User können das Gerät wechseln und ihre laufende Session (inkl. Handkarten) wiederherstellen.

## Login & Session-Hygiene

Um "Ghost-Sessions" (alte Spielerleichen in neuen Runden) zu vermeiden, gelten folgende Regeln:

1. **New Action = New Start:** Bei jedem `createLobby` oder `joinLobby` mit neuem Namen/ID wird die alte WebSocket-Verbindung hart getrennt und alle lokalen Session-Flags zurückgesetzt.
2. **Identity Hoisting:** Wenn ein Nutzer sich einloggt, während noch eine Gast-Session aktiv ist, wird die Gast-Session verworfen.
3. **Navigation Security:** Der `GameScreen` wird nur geöffnet, wenn die lokale `playerId` in der `players`-Liste des vom Server empfangenen `LobbyState` enthalten ist.

## Reconnect-Verhalten

| Szenario | Verhalten |
| :--- | :--- |
| App Crash (Gast) | Auto-Reconnect versucht die Session über die gespeicherte `playerId` zu retten. |
| Server Neustart | Der Reconnect schlägt fehl (404). Die App löscht die Session lautlos und zeigt die Lobby-Liste. |
| Manueller Re-Login | Nutzer loggt sich ein -> App sieht, dass er noch in einer Lobby ist -> Lädt GameState & Karten via HTTP -> Navigiert ins Spiel. |
