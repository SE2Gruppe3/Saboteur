package com.aau.saboteur.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aau.saboteur.network.ConnectivityApi
import com.aau.saboteur.network.NetworkConstants

@Composable
fun ConnectivityTestScreen() {
    var isLoading by remember { mutableStateOf(false) }
    var resultText by remember {
        mutableStateOf(
            "Tap the button to test the backend connection.\nCurrent base URL: ${NetworkConstants.baseUrl}"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Backend Connection Test",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "This sends a GET request to ${NetworkConstants.pingEndpoint}",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = {
                if (isLoading) return@Button
                isLoading = true
                resultText = "Checking connection to ${NetworkConstants.baseUrl}..."
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

@Composable
private fun NetworkRequestEffect(onComplete: (String) -> Unit) {
    LaunchedEffect(Unit) {
        onComplete(ConnectivityApi.runConnectionTest())
    }
}
