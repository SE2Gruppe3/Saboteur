# Saboteur - Backend Architecture (Phase 2)

## WebSocket Event System

Das System nutzt ein Command/Event-Pattern für die Echtzeit-Kommunikation.

### Inbound Commands (Client -> Server)
Commands werden als JSON an den WebSocket gesendet:
`{ "type": "COMMAND_NAME", "data": { ... } }`

- `REGISTER`: Verknüpft eine WebSocket-Session mit einer `playerId` und einem `lobbyCode`.
- `LOBBY_CREATE` / `LOBBY_JOIN`: Erstellt oder tritt einer Lobby bei.
- `START_GAME`: Startet das Spiel (nur Host).
- `PLAY_CARD` / `DISCARD_CARD`: Führt einen Spielzug aus.
- `GET_VALID_POSITIONS`: Fragt valide Board-Positionen für eine Karte ab.

### Outbound Events (Server -> Client)
Events sind typisiert und folgen der `GameEvent` Struktur:

- `LOBBY_STATE_UPDATE`: Aktueller Zustand der Lobby (Spielerliste, Host).
- `GAME_STATE_UPDATE`: Board-Placements und Turn-Informationen.
- `PLAYER_DATA`: Private Informationen (Rolle).
- `CARDS_DEALT`: Handkarten Updates.
- `ERROR`: Fehlermeldungen bei fehlgeschlagenen Validierungen.

## Persistence & Recovery
- **H2 Datenbank**: Jede Aktion wird sofort persistiert.
- **Auto-Recovery**: Bei Server-Neustart werden alle Lobbies und Spiele aus der DB rekonstruiert.
- **Reconnect**: Über den `/api/lobby/reconnect` REST-Endpunkt können Clients ihren vollständigen Zustand (inkl. Handkarten) wiederherstellen.

## Monitoring & Health (Actuator)
Der Server stellt umfassende Health-Checks bereit:
- **Basis Health**: `GET /actuator/health` (Gesamtstatus UP/DOWN)
- **Liveness & Readiness**: `/actuator/health/liveness` und `/actuator/health/readiness` (für Docker/K8s)
- **Game System Metrics**: `/actuator/health/gameSystem` liefert Details zu aktiven Lobbies und WebSocket-Verbindungen.

Detaillierte Dokumentation dazu findest du in [MONITORING_AND_HEALTH.md](docs/MONITORING_AND_HEALTH.md).

## Skalierbarkeit
Die Architektur ist "Stateless-Ready". Durch den Austausch der In-Memory Repositories gegen Redis und die Nutzung von Redis Pub/Sub im `MessagingService` kann das System horizontal skaliert werden.
