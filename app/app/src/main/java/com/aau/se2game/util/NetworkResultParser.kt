package com.aau.se2game.util

object NetworkResultParser {
    fun formatResult(statusCode: Int, body: String): String {
        return if (statusCode in 200..299) {
            "Success: HTTP $statusCode\n$body"
        } else {
            "Failed: HTTP $statusCode\n$body"
        }
    }
}
