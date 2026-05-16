# Teststrategie Saboteur

## 1. Test-Frameworks
- **JUnit 5**: Standard für alle Tests.
- **Mockito-Kotlin**: Zwingend erforderlich für Mocking in Kotlin, um NPEs bei Non-Nullable Typen zu vermeiden.
- **MockMvc**: Für Controller-Tests.
- **H2**: In-Memory DB für Repository-Tests.

## 2. Unit-Tests
Fokus auf reine Geschäftslogik ohne Spring-Kontext:
- **TurnManagerTest**: Validierung von Spielzügen, Karten-Anschlussregeln und Siegbedingungen.
- **LobbyServiceTest**: Testen von Spieler-Management und Cleanup-Logik.

## 3. WebSocket-Handler Tests
Jeder `CommandHandler` besitzt einen dedizierten Test, der:
- Die korrekte Interaktion mit den Services prüft.
- Den Versand der richtigen `GameEvent`s via `MessagingService` verifiziert.
- Exception-Handling bei ungültigen Zügen oder falscher PlayerId testet.

## 4. Integration-Tests
- **LobbyControllerTest**: Testet die REST-API und die Initialisierung der Session.
- **PersistenceRecoveryIntegrationTest**: Stellt sicher, dass das Laden von Spielständen aus der DB nach einem Server-Neustart funktioniert.

## 5. Coverage-Ziele
- **Core Business Logic**: > 90%
- **Websocket Handlers**: > 85%
- **Gesamtprojekt**: > 80%
