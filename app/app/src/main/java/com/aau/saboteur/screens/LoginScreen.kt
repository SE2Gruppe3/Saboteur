package com.aau.saboteur.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aau.saboteur.R // FIX PROBLEM 4: Import statt voller Pfad
import com.aau.saboteur.ui.resources.AppStrings
import com.aau.saboteur.ui.resources.GermanStrings

@Composable
fun LoginScreen(
    strings: AppStrings = GermanStrings,
    // FIX PROBLEM 1 & 2: States werden von außen gesteuert (Hoisting)
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onAuthClick: (String, String?, Boolean) -> Unit = { _, _, _ -> }
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // FIX PROBLEM 3 & 5: Konsequentes Trimming & saubere Formatierung
    val trimmedUsername = username.trim()
    val isUsernameValid = trimmedUsername.length >= 3
    val isPasswordValid = password.isEmpty() || password.length >= 6
    val canSubmit = isUsernameValid && isPasswordValid && !isLoading

    // FIX PROBLEM 3: Konsistente Prüfung für das Button-Label
    val isGuestAttempt = trimmedUsername.isNotBlank() && password.isBlank()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_pickaxe),
                contentDescription = "Saboteur Logo",
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = strings.loginTitle,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            // FIX PROBLEM 2: Visuelle Fehlermeldung anzeigen
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Username Feld
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(strings.usernameLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                isError = errorMessage != null, // Feld wird rot bei Fehler
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Passwort Feld
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(strings.passwordLabel) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                isError = errorMessage != null, // Feld wird rot bei Fehler
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    // Nur den Callback feuern, Parent setzt isLoading auf true
                    onAuthClick(trimmedUsername, if (password.isBlank()) null else password, password.isBlank())
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(text = if (isGuestAttempt) strings.guestJoinButton else strings.loginButton)
                }
            }
        }
    }
}