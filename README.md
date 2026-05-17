# Saboteur – SE2 Gruppe 3

> Digitale Multiplayer-Implementierung des Kartenspiels **Saboteur** (AMIGO) als Android-App  
> Entwickelt im Rahmen von Software Engineering 2 an der Universität Klagenfurt

---

## Team

| Name | Zuständigkeit |
|------|--------------|
| Sebastian Maier | Scrum Master · Spiellogik · Zugverwaltung · Pfad-Algorithmus · Board UI · Datenmodell |
| Lukas | UI Screens · App-Architektur · Jetpack Compose |
| Chris | Login/Registrierung · Session · WebSocket-Networking |
| Bastian | Verbindungslogik · Unit Tests |

---

## Spielbeschreibung

Saboteur ist ein Kartenspiel für **3–5 Spieler**. Die Spieler schlüpfen in die Rolle von Zwergen, die einen Tunnel zum Gold graben – oder als Saboteure den Bau heimlich sabotieren.

Jede Runde legen die Spieler reihum **Tunnelkarten** an das bestehende Wegenetz an. Die Zwerge versuchen, einen lückenlosen Pfad von der Startkarte zu einer der Zielkarten (mit Gold) zu legen. Die Saboteure verhindern genau das. Am Rundenende werden die Rollen aufgedeckt und Punkte vergeben.

---

## Tech Stack

| Bereich | Technologie |
|---------|------------|
| Mobile Frontend | Kotlin · Jetpack Compose · Android |
| Backend | Kotlin · Spring Boot |
| Shared Modul | Kotlin Multiplatform (KMP) |
| Kommunikation | WebSockets (Echtzeit-Multiplayer) |
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

Die App nutzt den **Beschleunigungssensor (Accelerometer)** des Android-Geräts:

> **Schütteln** → Cheat-Funktion: Kurzzeitige Anzeige, welche Zielkarte das Gold enthält (nur für den schüttelnden Spieler sichtbar, 3 Sekunden).

Implementiert in `app/` via `SensorManager` (Android SDK).

---

## Setup & Build

### Voraussetzungen

- Android Studio (Hedgehog oder neuer)
- JDK 17
- Android Emulator (API 34+) oder physisches Gerät
- (Optional) Docker für lokalen Server-Start

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

## Spielmodi & Einschränkungen

- **3–10 Spieler** (Echtzeit-Multiplayer über WebSockets)
- Login via **Benutzername + Passwort** (keine E-Mail)
- Spielbeitritt über **Session-ID**
- Keine KI-Gegner
- Aktionskarten (Reparatur, Sabotage, Karte ansehen) sind als optionales Feature vorgesehen
