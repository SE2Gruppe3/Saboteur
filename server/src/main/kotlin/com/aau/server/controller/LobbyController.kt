package com.aau.server.controller

import com.aau.saboteur.model.LobbyCreateRequest
import com.aau.saboteur.model.LobbyJoinRequest
import com.aau.saboteur.model.LobbyState
import com.aau.server.service.LobbyService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/lobby")
@CrossOrigin(origins = ["*"])
class LobbyController(
    private val lobbyService: LobbyService
) {

    @PostMapping("/create")
    fun create(@RequestBody req: LobbyCreateRequest): LobbyState =
        lobbyService.createLobby(req.playerName)

    @PostMapping("/join")
    fun join(@RequestBody req: LobbyJoinRequest): LobbyState =
        lobbyService.joinLobby(req.lobbyCode, req.playerName)

    @GetMapping("/{code}")
    fun get(@PathVariable code: String): LobbyState =
        lobbyService.getLobby(code)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "bad request")))

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(ex: IllegalStateException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(409).body(mapOf("error" to (ex.message ?: "conflict")))
}