# Deployment & Release

## Release-Prozess

Jeder Merge auf `main` löst automatisch den GitHub Actions Workflow **"Build and Publish Docker Image"** aus:

1. Gradle baut das Spring Boot JAR (`./gradlew :server:bootJar -x test`)
2. Docker-Image wird gebaut und als `ghcr.io/se2gruppe3/saboteur:latest` in die GitHub Container Registry (GHCR) gepusht
3. Ein Release entspricht dem aktuellen `latest`-Image auf dem Produktivserver

Ein neues Release wird also durch einen Merge auf `main` ausgelöst — kein manueller Tag oder GitHub Release notwendig.

---

## Umgebungsvariablen

Die Datei `/home/grp-3/.env` auf dem Produktivserver wird beim Start in den Container eingebunden (`/app/.env`) und muss folgende Variablen enthalten:

```env
# Datenbank-URL (H2 persistent, Pfad innerhalb des Docker-Volumes)
SPRING_DATASOURCE_URL=jdbc:h2:file:/data/saboteur_db;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE
```

> **Hinweis:** Ist `SPRING_DATASOURCE_URL` nicht gesetzt, verwendet der Server den Standard-Pfad `~/.saboteur/saboteur_db` innerhalb des Containers. Im Produktivbetrieb sollte der Pfad auf das persistente Volume `/data` zeigen.

---

## Deployment auf dem Produktivserver

Voraussetzungen: SSH-Zugang zum Server, Docker und Docker Compose installiert.

```bash
# 1. Per SSH auf den Server verbinden
ssh grp-3@<server-adresse>

# 2. In das Projektverzeichnis wechseln
cd /home/grp-3

# 3. Neues Image laden
docker compose pull

# 4. Container neu starten
docker compose up -d

# 5. Logs prüfen
docker compose logs -f
```

Der Server ist danach unter Port `53207` erreichbar. Der Spring Boot Actuator Health-Endpoint ist unter `http://<server>:53207/actuator/health` verfügbar.

---

## Lokale Entwicklung

### Voraussetzungen
- JDK 21
- Android Studio (für App-Entwicklung)
- Android Emulator (API 34+) oder physisches Gerät

```bash
# Server lokal starten (Port 8080)
./gradlew :server:bootRun

# Android-App auf Emulator/Gerät deployen
./gradlew :app:installDebug

# Alle Tests ausführen
./gradlew test

# Nur Server-Tests
./gradlew :server:test
```

> **Build Variant:** In Android Studio auf `Release` umstellen, damit die App automatisch den Uni-Server statt `localhost` verwendet.

---

## Rollback

Um auf eine ältere Version zurückzuwechseln, das Image-Tag in `docker-compose.yaml` anpassen:

```bash
# 1. Laufenden Container stoppen
docker compose down

# 2. docker-compose.yaml anpassen:
#    image: ghcr.io/se2gruppe3/saboteur:sha-<commit-hash>
#    (konkreten Commit-Hash aus der GHCR-Paketliste entnehmen)

# 3. Container mit altem Image starten
docker compose up -d
```

Verfügbare Image-Tags sind unter `https://github.com/SE2Gruppe3/Saboteur/pkgs/container/saboteur` einsehbar.

---

## Weiterführende Dokumentation

- [Monitoring & Health Checks](MONITORING_AND_HEALTH.md)
- [Session & WebSocket-Architektur](SESSION_AND_WEBSOCKET_ARCHITECTURE.md)
- [Reconnect-Protokoll](RECONNECT_PROTOCOL.md)
