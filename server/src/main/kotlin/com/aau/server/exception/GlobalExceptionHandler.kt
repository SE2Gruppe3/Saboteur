package com.aau.server.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("Bad request / Ungültige Anfrage: ${ex.message}")
        return ResponseEntity.badRequest().body(
            ErrorResponse("BAD_REQUEST", ex.message ?: "Ungültige Anfrage / Invalid request")
        )
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        logger.warn("Not found / Nicht gefunden: ${ex.message}")
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse("NOT_FOUND", ex.message ?: "Nicht gefunden / Not found")
        )
    }

    @ExceptionHandler(SecurityException::class)
    fun handleForbidden(ex: SecurityException): ResponseEntity<ErrorResponse> {
        logger.warn("Forbidden / Nicht autorisiert: ${ex.message}")
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse("FORBIDDEN", ex.message ?: "Nicht autorisiert / Unauthorized")
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled exception / Unbehandelter Fehler", ex)
        return ResponseEntity.internalServerError().body(
            ErrorResponse("INTERNAL_ERROR", "Ein interner Fehler ist aufgetreten / An internal error occurred")
        )
    }
}

data class ErrorResponse(val code: String, val message: String)
