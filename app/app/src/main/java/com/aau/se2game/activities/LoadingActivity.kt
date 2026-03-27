package com.aau.se2game.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aau.se2game.ui.theme.SE2GameTheme
import com.aau.se2game.navigation.NavigationMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI

class LoadingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SE2GameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box {
                        LoadingScreen()
                        NavigationMenu()
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    var isLoading by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("Tap to test backend connection.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Loading & Connection Test",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (isLoading) return@Button
                isLoading = true
                resultText = "Checking connection..."
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Run Connection Test")
        }
        if (isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
            LaunchedEffect(Unit) {
                resultText = runConnectionTest()
                isLoading = false
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = resultText,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private suspend fun runConnectionTest(): String = withContext(Dispatchers.IO) {
    // Using URI to avoid URL(String) deprecation
    val url = URI.create("http://10.0.2.2:8080/api/ping").toURL()
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 5_000
        readTimeout = 5_000
    }

    try {
        val statusCode = connection.responseCode
        val bodyReader = if (statusCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val body = bodyReader?.use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        }.orEmpty()

        if (statusCode in 200..299) {
            "Success: HTTP $statusCode\n$body"
        } else {
            "Failed: HTTP $statusCode\n$body"
        }
    } catch (exception: Exception) {
        "Connection error: ${exception.message}"
    } finally {
        connection.disconnect()
    }
}
