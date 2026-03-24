package com.aau.se2game

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import com.aau.se2game.ui.theme.SE2GameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SE2GameTheme {
                ConnectivityTestScreen(modifier = Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
fun ConnectivityTestScreen(modifier: Modifier = Modifier) {
    var isLoading by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("Tap the button to test the backend connection.") }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Backend Connection Test",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "This sends a GET request to http://10.0.2.2:8080/api/ping from the emulator.",
            style = MaterialTheme.typography.bodyMedium
        )
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
            NetworkRequestEffect(
                onComplete = { response ->
                    resultText = response
                    isLoading = false
                }
            )
            CircularProgressIndicator()
        }
        Text(
            text = resultText,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectivityTestPreview() {
    SE2GameTheme {
        ConnectivityTestScreen()
    }
}

@Composable
private fun NetworkRequestEffect(onComplete: (String) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        onComplete(runConnectionTest())
    }
}

private suspend fun runConnectionTest(): String = withContext(Dispatchers.IO) {
    val connection = (URL("http://10.0.2.2:8080/api/ping").openConnection() as HttpURLConnection).apply {
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
