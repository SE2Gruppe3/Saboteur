package com.aau.saboteur.network

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

object WebSocketManager {
    private val _messages = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 100)
    val messages: SharedFlow<Pair<String, String>> = _messages.asSharedFlow()

    private val _errorMessages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 10)
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var isConnecting = false
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectDelay = 2000L

    fun connect() {
        val endpoint = NetworkConstants.mainWebSocketEndpoint
        if (webSocket != null || isConnecting || endpoint.isBlank() || !endpoint.startsWith("ws")) return

        isConnecting = true
        val request = Request.Builder()
            .url(endpoint)
            .build()
        
        webSocket = HttpClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnecting = false
                reconnectDelay = 2000L
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    val data = json.opt("data")?.toString() ?: ""
                    _messages.tryEmit(type to data)
                } catch (e: Exception) {
                    _errorMessages.tryEmit("Failed to parse websocket message: ${e.message}")
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorMsg = response?.let { "Connection failed: ${it.code}" } ?: "Connection failed: ${t.message}"
                _errorMessages.tryEmit(errorMsg)
                t.printStackTrace()
                
                handleReconnect()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                handleReconnect()
            }
        })
    }

    private fun handleReconnect() {
        webSocket = null
        isConnecting = false
        
        handler.postDelayed({
            connect()
        }, reconnectDelay)
        
        reconnectDelay = (reconnectDelay * 2).coerceAtMost(30000L)
    }

    fun sendMessage(type: String, data: Any) {
        val message = JSONObject().apply {
            put("type", type)
            put("data", data)
        }.toString()
        
        val sent = webSocket?.send(message) ?: false
        if (!sent) {
            _errorMessages.tryEmit("Failed to send message: $type. Connection might be down.")
        }
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "App closing")
        webSocket = null
        isConnecting = false
    }

    fun reset() {
        close()
        reconnectDelay = 2000L
    }
}
