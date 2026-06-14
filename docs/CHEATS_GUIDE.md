# Saboteur Cheats Guide

Dieses Dokument beschreibt das erweiterbare Cheat-System für das Saboteur-Spiel. Das System ist modular aufgebaut und serverseitig gekapselt, um Manipulationen am Spielzustand sicher und typsicher zu ermöglichen.

## Architektur-Übersicht

Das Cheat-System basiert auf einem generischen WebSocket-Kommando `PLAYER_CHEAT`. 
Das Entlarven von Cheats läuft über ein separates Kommando `ACCUSE_CHEAT`, damit echte Cheat-Ausführung und Spieler-Beschuldigungen sauber getrennt bleiben.

### WebSocket-Kommando: `PLAYER_CHEAT`

**Payload:**
- `lobbyCode` (String): Der Code der aktuellen Lobby.
- `cheatType` (CheatType): Ein Enum-Wert, der den Cheat identifiziert (z. B. `LANTERN_FLASHLIGHT`).

**Sicherheitshinweis:** Der Parameter `consumeTurn` wird NICHT vom Client gesendet. Der Server entscheidet selbstständig auf Basis des `cheatType`, ob die Aktion einen Spielzug verbraucht oder nicht. Dies verhindert, dass Clients schummeln, ohne ihren Zug zu beenden, wenn dies eigentlich erforderlich wäre.

### WebSocket-Kommando: `ACCUSE_CHEAT`

**Payload:**
- `lobbyCode` (String): Der Code der aktuellen Lobby.
- `accusedPlayerId` (String): Der Spieler, der des Schummelns beschuldigt wird.

Der Server bestimmt den beschuldigenden Spieler immer aus der WebSocket-Session. Das Ergebnis wird als `CHEAT_ACCUSATION_RESULT` an die gesamte Lobby gesendet.

## Implementierung neuer Cheats

### 1. Cheat-Typ definieren (Shared)

Füge in der Datei `CheatType.kt` (im `shared`-Modul) einen neuen Enum-Wert hinzu:
```kotlin
@Serializable
enum class CheatType {
    LANTERN_FLASHLIGHT,
    VOLUME_SEQUENCE_DISCARD,
    MY_NEW_CHEAT // Neuer Cheat hier
}
```

### 2. Backend-Logik (:server)

Die Logik wird zentral im `TurnManager.kt` in der Methode `cheatPlayer` implementiert.

**Beispiel für die Logik:**
```kotlin
val consumeTurn = when (cheatType) {
    CheatType.MY_NEW_CHEAT -> {
        // 1. Validierung (optional)
        // require(state.currentPlayerId == playerId) { "Nicht am Zug" }
        
        // 2. Zustand manipulieren
        // internal.gameState = ...
        
        true // Gibt zurück, ob der Zug verbraucht wird
    }
    CheatType.LANTERN_FLASHLIGHT -> {
        // ... (Zustand manipulieren)
        false // Verbraucht keinen Zug
    }
}
```

### 3. Frontend (:app)

Im Frontend wird der Cheat über das `GameViewModel` ausgelöst:
```kotlin
viewModel.triggerCheat(CheatType.MY_NEW_CHEAT)
```

## Bestehende Cheats

### LANTERN_FLASHLIGHT
- **Beschreibung:** Hebt die Laternen-Blockade des Spielers auf.
- **Hardware-Trigger:** Wird in `GameScreen.kt` durch den `CameraManager.TorchCallback` (physische Taschenlampe) ausgelöst.
- **Bedingung:** Spieler muss am Zug sein (serverseitig validiert).
- **Runden-Logik:** Verbraucht KEINEN Spielzug (`consumeTurn = false`).
- **Entlarven:** Wenn dadurch wirklich eine Laternen-Blockade entfernt wurde, hinterlegt der Server einen Beweis. Eine spätere Beschuldigung gegen diesen Spieler meldet `caught = true` und verbraucht den Beweis.

### VOLUME_SEQUENCE_DISCARD
- **Beschreibung:** Wirft serverseitig eine zufällige Handkarte des Spielers ab und zieht automatisch eine Ersatzkarte, falls der Nachziehstapel noch Karten enthält.
- **Hardware-Trigger:** Wird in `GameScreen.kt` durch die Sequenz `Lauter, Lauter, Leiser, Leiser` ausgelöst.
- **Bedingung:** Spieler muss nicht am Zug sein. Eine leere Hand ist ein sicherer No-op.
- **Runden-Logik:** Verbraucht KEINEN Spielzug (`consumeTurn = false`).
- **Entlarven:** Erfolgreiche Nutzung hinterlegt serverseitig einen Beweis. Eine spätere Beschuldigung gegen diesen Spieler meldet `caught = true` und verbraucht den Beweis.

## Anleitung für Entwickler

1. **Enum erweitern**: Neuen Typ in `CheatType` anlegen.
2. **Server-Handler**: Logik und Runden-Entscheidung in `TurnManager.cheatPlayer` ergänzen.
3. **Trigger binden**: Den Aufruf `triggerCheat` im Frontend an ein UI-Element oder einen Sensor-Event knüpfen.
4. **Broadcast**: Der Server verteilt den neuen Zustand automatisch an alle Teilnehmer.
