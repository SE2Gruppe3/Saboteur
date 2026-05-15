# Monitoring & Health Checks

Dieses Dokument beschreibt die Implementierung von Spring Boot Actuator zur Überwachung des Saboteur-Backends.

## 1. Übersicht

Der Server nutzt **Spring Boot Actuator**, um Vitalwerte der Anwendung bereitzustellen. Diese Endpoints werden für das Monitoring in Produktion sowie für Health-Checks in Container-Umgebungen (Docker, Kubernetes) verwendet.

Basispfad: `/actuator/health`

## 2. Verfügbare Endpoints

| Pfad | Beschreibung |
| :--- | :--- |
| `/actuator/health` | Gesamtstatus der Anwendung inkl. aller Komponenten. |
| `/actuator/health/liveness` | Liveness-Probe: Läuft der Prozess noch? |
| `/actuator/health/readiness` | Readiness-Probe: Ist die App bereit, Traffic zu empfangen? |
| `/actuator/health/db` | Detailstatus der H2-Datenbankverbindung. |
| `/actuator/health/gameSystem` | Status des Multiplayer-Systems (Lobbies, Sessions). |

## 3. GameSystem Health Indicator

Eine maßgeschneiderte Komponente (`GameSystemHealthIndicator`) liefert Echtzeit-Metriken über den aktuellen Spielbetrieb:

- **activeLobbies:** Anzahl der aktuell im Speicher/DB befindlichen Lobbies.
- **connectedWebSockets:** Anzahl der aktuell aktiven WebSocket-Verbindungen.
- **registeredPlayersInSessions:** Anzahl der Spieler, die einer Session zugeordnet sind.

### Schwellenwerte
Falls die Anzahl der aktiven Lobbies einen kritischen Wert überschreitet (aktuell auf > 1000 konfiguriert), wechselt der Status der Komponente auf `Slightly Overloaded`. Dies kann genutzt werden, um Loadbalancer anzuweisen, keine neuen Nutzer auf diesen Server-Knoten zu schicken.

## 4. Beispiel-Response (`GET /actuator/health`)

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "H2",
        "validationQuery": "isValid()"
      }
    },
    "gameSystem": {
      "status": "UP",
      "details": {
        "activeLobbies": 5,
        "connectedWebSockets": 12,
        "registeredPlayersInSessions": 12
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 512123543552,
        "free": 320512409600,
        "threshold": 10485760
      }
    },
    "livenessState": { "status": "UP" },
    "readinessState": { "status": "UP" }
  }
}
```

## 5. Konfiguration (application.properties)

```properties
# Sichtbarkeit der Details (always, when-authorized, never)
management.endpoint.health.show-details=always
management.endpoint.health.show-components=always

# Aktivierung der Liveness/Readiness Pfade
management.endpoint.health.probes.enabled=true
```

## 6. Verwendung mit Docker / Kubernetes

In der Deployment-Konfiguration können die Probes wie folgt eingebunden werden:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
```
