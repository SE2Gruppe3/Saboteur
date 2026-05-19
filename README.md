# Saboteur – SE2 Gruppe 3

> Digitale Multiplayer-Implementierung des Kartenspiels **Saboteur** (AMIGO) als Android-App  
> Entwickelt im Rahmen von Software Engineering 2 an der Universität Klagenfurt

---

## Team

| Sebastian |
| Lukas |
| Chris |
| Bastian |

---

## Spielbeschreibung

Saboteur ist ein Kartenspiel für **3–10 Spieler**. Die Spieler schlüpfen in die Rolle von Zwergen, die einen Tunnel zum Gold graben – oder als Saboteure den Bau heimlich sabotieren.

Jede Runde legen die Spieler reihum **Tunnelkarten** an das bestehende Wegenetz an. Die Zwerge versuchen, einen lückenlosen Pfad von der Startkarte zu einer der Zielkarten (mit Gold) zu legen. Die Saboteure verhindern genau das. Am Rundenende werden die Rollen aufgedeckt und Punkte vergeben.

Zusätzlich zu Wegkarten gibt es **Aktionskarten**: Sperr- und Entsperrkarten (Werkzeuge sabotieren/reparieren), Rockfall (Karte vom Spielfeld entfernen) sowie Zielkarte ansehen.

---

## Tech Stack

| Bereich | Technologie |
|---------|------------|
| Mobile Frontend | Kotlin · Jetpack Compose · Android |
| Backend | Kotlin · Spring Boot |
| Shared Modul | Kotlin Multiplatform (KMP) |
| Kommunikation | WebSockets (Echtzeit-Multiplayer) |
| Datenbank | H2 (In-Memory, Persistenz & Auto-Recovery) |
| Build | Gradle KTS (Monorepo) |
| Qualitätssicherung | SonarCloud · GitHub Actions CI |
| Design | Figma |

---

## Architektur

Das Projekt ist als **KMP-Monorepo** mit drei Modulen strukturiert:

```
Saboteur/
├── shared/     # Gemeinsame Datenmodelle und Enums (TunnelCard, Player, GameBoard, …)
├── app/        # Android Frontend (Jetpack Compose, UI-Logik, Asset-Mapping)
└── server/     # Spring Boot Backend (Spiellogik, Validierung, WebSocket-Server)
```

**Modulprinzip:** `shared/` enthält ausschließlich Datenklassen ohne ausführbare Logik. Spiellogik und Validierung liegen im `server/`, UI-spezifischer Code ausschließlich in `app/`.

---

## Gerätesensor-Feature

Die App integriert einen **Gerätesensor (Accelerometer)** für eine Cheat-Funktion. Die genaue Umsetzung wird in Sprint 3 festgelegt und implementiert.

---

## Setup & Build

### Voraussetzungen

- Android Studio (Hedgehog oder neuer)
- JDK 17
- Android Emulator (API 34+) oder physisches Gerät
- (Optional) Docker für lokalen Server-Start
- **Uni-Server:** Build Variant auf `Release` umstellen → App verbindet sich automatisch mit dem Uni-Server

### Projekt klonen

```bash
git clone https://github.com/SE2Gruppe3/Saboteur.git
cd Saboteur
```

### App bauen & starten

```bash
# Alle Module kompilieren
./gradlew build

# App auf Emulator/Gerät deployen
./gradlew :app:installDebug

# Server lokal starten
./gradlew :server:bootRun
```

### Tests ausführen

```bash
./gradlew test
```

---

## CI & Codequalität

[![Build Status](https://github.com/SE2Gruppe3/Saboteur/actions/workflows/sonarcloud.yml/badge.svg)](https://github.com/SE2Gruppe3/Saboteur/actions)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=SE2Gruppe3_Saboteur&metric=alert_status)](https://sonarcloud.io/project/overview?id=SE2Gruppe3_Saboteur)

Jeder Push und Pull Request durchläuft automatisch die SonarCloud-Qualitätsanalyse. Merges auf `main` erfordern mindestens **1 Reviewer-Approval** und ein bestandenes Quality Gate.

---

## WebSocket Event System

Die Echtzeit-Kommunikation folgt einem **Command/Event-Pattern**. Nachrichten werden als JSON übertragen: `{ "type": "COMMAND_NAME", "data": { ... } }`

**Inbound Commands (Client → Server)**

| Command | Beschreibung |
|---------|-------------|
| `REGISTER` | Verknüpft eine WebSocket-Session mit `playerId` und `lobbyCode` |
| `LOBBY_CREATE` / `LOBBY_JOIN` | Erstellt oder tritt einer Lobby bei |
| `START_GAME` | Startet das Spiel (nur Host) |
| `PLAY_CARD` / `DISCARD_CARD` | Führt einen Spielzug aus |
| `GET_VALID_POSITIONS` | Fragt valide Board-Positionen für eine Karte ab |

**Outbound Events (Server → Client)**

| Event | Beschreibung |
|-------|-------------|
| `LOBBY_STATE_UPDATE` | Aktueller Lobby-Zustand (Spielerliste, Host) |
| `GAME_STATE_UPDATE` | Board-Placements und Turn-Informationen |
| `PLAYER_DATA` | Private Informationen (Rolle) |
| `CARDS_DEALT` | Handkarten-Updates |
| `ERROR` | Fehlermeldungen bei fehlgeschlagenen Validierungen |

---

## Persistence & Reconnect

- **H2-Datenbank:** Jede Aktion wird sofort persistiert.
- **Auto-Recovery:** Bei Server-Neustart werden alle Lobbies und Spiele aus der DB rekonstruiert.
- **Reconnect:** Über `GET /api/lobby/reconnect` können Clients ihren vollständigen Zustand (inkl. Handkarten) wiederherstellen.

Weitere Details: [MONITORING_AND_HEALTH.md](https://github.com/SE2Gruppe3/Saboteur/blob/main/docs/MONITORING_AND_HEALTH.md)

---

## Spielmodi & Einschränkungen

- **3–10 Spieler** (Echtzeit-Multiplayer über WebSockets)
- Login via **Benutzername** (kein Passwort erforderlich, Gastbeitritt möglich)
- Spielbeitritt über **Session-ID**
- Keine KI-Gegner
- Gerätesensor-Cheat-Funktion folgt in Sprint 3
