package com.aau.se2game.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start LoadingActivity and finish MainActivity
        val intent = Intent(this, LoadingActivity::class.java)
        startActivity(intent)
        finish()
    }
}
