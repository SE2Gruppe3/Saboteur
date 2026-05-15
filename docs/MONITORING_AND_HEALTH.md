# Monitoring & Health Checks

Dieses Dokument beschreibt die Implementierung von Spring Boot Actuator zur Überwachung des Saboteur-Backends.

## 1. Installation

Um die Health-Checks zu nutzen, muss folgende Dependency in der `server/build.gradle.kts` vorhanden sein:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
```

## 2. Verfügbare Endpoints

| Pfad | Beschreibung |
| :--- | :--- |
| `/actuator/health` | Gesamtstatus der Anwendung inkl. aller Komponenten. |
| `/actuator/health/liveness` | Liveness-Probe: Läuft der Prozess noch? (Für Docker/K8s restarts) |
| `/actuator/health/readiness` | Readiness-Probe: Ist die App bereit für Traffic? (DB-Check, etc.) |
| `/actuator/health/db` | Detailstatus der Datenbankverbindung (H2). |
| `/actuator/health/gameSystem` | Status des Multiplayer-Systems (Lobbies, Sessions). |

## 3. GameSystem Health Indicator

Die Komponente `GameSystemHealthIndicator` liefert Echtzeit-Metriken über den Spielbetrieb:

- **activeLobbies:** Anzahl der aktuell im Speicher/DB befindlichen Lobbies.
- **connectedWebSockets:** Anzahl der aktuell aktiven WebSocket-Verbindungen.

### Schwellenwerte
- **UP:** Normalbetrieb.
- **Slightly Overloaded:** Wird ausgelöst, wenn mehr als 1000 Lobbies aktiv sind. Dies signalisiert dem Monitoring, dass Ressourcen knapp werden könnten.

## 4. Konfiguration (application.properties)

Die aktuelle Konfiguration ist für Transparenz im Debugging und Stabilität in Clustern optimiert:

```properties
# Sichtbarkeit der Details (always zeigt volle JSON-Struktur)
management.endpoint.health.show-details=always
management.endpoint.health.show-components=always

# Aktivierung der Liveness/Readiness Pfade
management.endpoint.health.probes.enabled=true
```

## 5. Erweiterung auf PostgreSQL

Sobald das Projekt auf PostgreSQL umgestellt wird, erkennt Actuator dies automatisch über den JDBC-Treiber. Es sind keine Code-Änderungen am Health-System nötig; der `/actuator/health/db` Pfad wird automatisch die PostgreSQL-Konnektivität prüfen.

## 6. Docker Healthcheck Beispiel

In einer `docker-compose.yaml` kann der Status wie folgt abgefragt werden:

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health/liveness"]
  interval: 30s
  timeout: 10s
  retries: 3
```
