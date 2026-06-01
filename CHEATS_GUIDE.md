# Saboteur Cheats Guide

Dieses Dokument beschreibt das erweiterbare Cheat-System für das Saboteur-Spiel. Das System ist modular aufgebaut, um verschiedene Arten von Manipulationen am Spielzustand zu ermöglichen.

## Architektur-Übersicht

Das Cheat-System basiert auf einem generischen WebSocket-Kommando `PLAYER_CHEAT`. Dies ermöglicht es Clients, Aktionen auszuführen, die über die regulären Spielzüge (Karten legen, abwerfen) hinausgehen.

### WebSocket-Kommando: `PLAYER_CHEAT`

**Payload:**
- `lobbyCode` (String): Der Code der aktuellen Lobby.
- `cheatType` (String): Ein eindeutiger Bezeichner für den Cheat (z. B. `"LANTERN_FLASHLIGHT"`).
- `consumeTurn` (Boolean): Bestimmt, ob der Cheat den aktuellen Spielzug beendet (`true`) oder ob der Spieler danach noch eine reguläre Aktion ausführen darf (`false`).

## Implementierung neuer Cheats

### 1. Backend (:server)

Neue Cheats werden zentral im `TurnManager.kt` in der Methode `cheatPlayer` implementiert.

**Beispiel für die Logik:**
```kotlin
when (cheatType) {
    "MY_NEW_CHEAT" -> {
        // 1. Validierung (Falls der Cheat nur im eigenen Zug erlaubt ist)
        // require(state.currentPlayerId == playerId) { "Nicht dein Zug!" }
        
        // 2. Spielzustand manipulieren
        // internal.gameState = ...
    }
}
```

**Wichtige Design-Prinzipien:**
- **Flexibilität der Zug-Validierung:** Cheats müssen nicht zwingend an den eigenen Zug gebunden sein. Die Prüfung `state.currentPlayerId == playerId` sollte nur dann durchgeführt werden, wenn der spezifische Cheat dies erfordert.
- **Zustands-Manipulation:** Cheats können den `GameState` direkt verändern (z. B. Rollen aufdecken, Handkarten tauschen, Blockaden lösen).
- **Runden-Steuerung:** Die Logik für `consumeTurn` wird automatisch am Ende der `cheatPlayer`-Methode verarbeitet.

### 2. Frontend (:app)

Im Frontend erfolgt die Auslösung über das `GameViewModel` und das `GameApi`.

**ViewModel-Aufruf:**
```kotlin
viewModel.triggerCheat("MY_NEW_CHEAT", consumeTurn = true)
```

**Sensor-Integration:**
Cheats können durch UI-Elemente oder Hardware-Events (wie Sensoren, Taschenlampe, Schütteln) ausgelöst werden. In `GameScreen.kt` finden sich Beispiele für die Nutzung von `DisposableEffect` zur Registrierung von System-Callbacks.

## Bestehende Cheats

### LANTERN_FLASHLIGHT (Issue #123)
- **Beschreibung:** Hebt die Laternen-Blockade des Spielers auf, indem die physische Taschenlampe des Handys eingeschaltet wird.
- **Bedingung:** Muss am Zug sein.
- **Standard-Konfiguration:** `consumeTurn = false` (Spielzug wird nicht verbraucht).

## Anleitung für Entwickler (Zukunftssicherung)

1.  **Typ definieren:** Füge einen neuen String-Key für `cheatType` hinzu.
2.  **Backend-Logik:** Implementiere die gewünschte Zustandsänderung in `TurnManager.cheatPlayer`.
3.  **Frontend-Trigger:** Erstelle einen Button oder binde einen Sensor im `GameScreen` ein, der `viewModel.triggerCheat` aufruft.
4.  **Broadcast:** Der Server sendet nach jedem Cheat automatisch den aktualisierten `GameState` an alle Clients.
