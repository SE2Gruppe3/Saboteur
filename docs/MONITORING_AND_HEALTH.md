# Monitoring & Health Checks

Dieses Dokument beschreibt die Implementierung von Spring Boot Actuator zur Überwachung des Saboteur-Backends.

## 1. Übersicht

Der Server nutzt **Spring Boot Actuator**, um Vitalwerte der Anwendung bereitzustellen. Diese Endpoints werden für das Monitoring in Produktion sowie für Health-Checks in Container-Umgebungen (Docker, Kubernetes) verwendet.

Basispfad: `/actuator/health`

## 2. Verfügbare Endpoints

| Pfad | Beschreibung |
| :--- | :--- |
| `/actuator/health` | Gesamtstatus der Anwendung inkl. aller Komponenten. |
| `/actuator/health/liveness` | Liveness-Probe: Prüft ob die JVM noch läuft. |
| `/actuator/health/readiness` | Readiness-Probe: Prüft ob die App (DB + Spielsystem) bereit ist. |
| `/actuator/health/db` | Status der H2-Datenbankverbindung. |
| `/actuator/health/gameSystem` | Metriken des Multiplayer-Systems. |

## 3. GameSystem Health Indicator

Die Komponente `GameSystemHealthIndicator` liefert Echtzeit-Metriken über den aktuellen Spielbetrieb:

- **activeLobbies:** Anzahl der aktuell im Speicher/DB befindlichen Lobbies.
- **connectedWebSockets:** Anzahl der aktuell aktiven WebSocket-Verbindungen.
- **registeredPlayersInSessions:** Anzahl der Spieler, die einer Session zugeordnet sind.

### Schwellenwerte & Alarme
- **UP:** Normalbetrieb.
- **Slightly Overloaded:** Wird ausgelöst, wenn mehr als 500 Lobbies aktiv sind. 
- **DOWN:** Wird ausgelöst, wenn kritische Services (wie der `MessagingService`) nicht mehr erreichbar sind.

## 4. Konfiguration (application.properties)

Die Sichtbarkeit ist auf `always` gestellt, um im Debugging volle Transparenz über alle Komponenten zu haben:

```properties
management.endpoint.health.show-details=always
management.endpoint.health.show-components=always
management.endpoint.health.probes.enabled=true
```

## 5. Deployment Integration

### Docker Healthcheck
In der `docker-compose.yaml` sollte die Readiness-Probe verwendet werden:
```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health/readiness"]
  interval: 10s
  timeout: 5s
  retries: 3
```

### Kubernetes
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
