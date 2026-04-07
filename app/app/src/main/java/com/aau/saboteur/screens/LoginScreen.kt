package com.aau.saboteur.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aau.saboteur.ui.resources.AppStrings
import com.aau.saboteur.ui.resources.GermanStrings

@Composable
fun LoginScreen(
    strings: AppStrings = GermanStrings,
    onAuthClick: (String, String?, Boolean) -> Unit = { _, _, _ -> }
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }


    var isLoading by remember { mutableStateOf(false) }

    // Validierung: Name mind. 3 Zeichen, Passwort entweder leer (Gast) oder mind. 6 Zeichen
    val isUsernameValid = username.trim().length >= 3
    val isPasswordValid = password.isEmpty() || password.length >= 6
    val canSubmit = isUsernameValid && isPasswordValid && !isLoading

    val isGuestAttempt = username.isNotBlank() && password.isBlank()

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
                painter = painterResource(id = com.aau.saboteur.R.drawable.ic_pickaxe),
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

            Spacer(modifier = Modifier.height(32.dp))

            // Username Feld
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(strings.usernameLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    isLoading = true // Zeige dem User, dass was passiert
                    val isGuest = password.isBlank()
                    onAuthClick(username, if (isGuest) null else password, isGuest)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmit, // Nur aktiv wenn Validierung OK
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
                    Text(
                        text = if (isGuestAttempt) strings.guestJoinButton else strings.loginButton
                    )
                }
            }
        }
    }
}