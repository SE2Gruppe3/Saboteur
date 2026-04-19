package com.aau.saboteur.network

import android.app.Application

class WebSocketApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WebSocketManager.connect()
    }
}