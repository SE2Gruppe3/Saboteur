# Deployment & Release

## Release Process

Every merge into `main` automatically triggers the GitHub Actions workflow **"Build and Publish Docker Image"**:

1. Gradle builds the Spring Boot JAR (`./gradlew :server:bootJar -x test`)
2. A Docker image is built and pushed as `ghcr.io/se2gruppe3/saboteur:latest` to the GitHub Container Registry (GHCR)
3. A release corresponds to the current `latest` image running on the production server

A new release is triggered by merging into `main` — no manual tag or GitHub Release is required.

---

## Environment Variables

The file `/home/grp-3/.env` on the production server is mounted into the container at startup (`/app/.env`) and must contain the following variables:

```env
# Database URL (persistent H2, path inside the Docker volume)
SPRING_DATASOURCE_URL=jdbc:h2:file:/data/saboteur_db;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE
```

> **Note:** If `SPRING_DATASOURCE_URL` is not set, the server defaults to `~/.saboteur/saboteur_db` inside the container. In production the path should point to the persistent volume at `/data`.

---

## Deployment on the Production Server

Prerequisites: SSH access to the server, Docker and Docker Compose installed.

```bash
# 1. Connect to the server via SSH
ssh grp-3@<server-address>

# 2. Navigate to the project directory
cd /home/grp-3

# 3. Pull the latest image
docker compose pull

# 4. Restart the container
docker compose up -d

# 5. Check the logs
docker compose logs -f
```

The server will be reachable on port `53207`. The Spring Boot Actuator health endpoint is available at `http://<server>:53207/actuator/health`.

---

## Local Development

### Prerequisites
- JDK 21
- Android Studio (for app development)
- Android Emulator (API 34+) or a physical device

```bash
# Start the server locally (port 8080)
./gradlew :server:bootRun

# Deploy the Android app to emulator/device
./gradlew :app:installDebug

# Run all tests
./gradlew test

# Run server tests only
./gradlew :server:test
```

> **Build Variant:** Switch to `Release` in Android Studio so the app automatically connects to the university server instead of `localhost`.

---

## Rollback

To roll back to a previous version, change the image tag in `docker-compose.yaml`:

```bash
# 1. Stop the running container
docker compose down

# 2. Edit docker-compose.yaml:
#    image: ghcr.io/se2gruppe3/saboteur:sha-<commit-hash>
#    (find the exact commit hash in the GHCR package list)

# 3. Start the container with the old image
docker compose up -d
```

Available image tags can be found at `https://github.com/SE2Gruppe3/Saboteur/pkgs/container/saboteur`.

---

## Further Documentation

- [Monitoring & Health Checks](MONITORING_AND_HEALTH.md)
- [Session & WebSocket Architecture](SESSION_AND_WEBSOCKET_ARCHITECTURE.md)
- [Reconnect Protocol](RECONNECT_PROTOCOL.md)
