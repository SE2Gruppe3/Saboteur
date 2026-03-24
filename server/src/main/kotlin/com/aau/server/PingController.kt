package com.aau.server

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class PingController {

    @GetMapping("/ping")
    fun ping(): PingResponse = PingResponse(
        status = "ok",
        message = "Frontend can reach the backend"
    )
}

data class PingResponse(
    val status: String,
    val message: String
)
