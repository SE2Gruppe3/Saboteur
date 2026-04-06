package com.aau.saboteur

import android.app.Application
import com.aau.saboteur.network.WebSocketManager

class WebSocketApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WebSocketManager.connect()
    }
}
