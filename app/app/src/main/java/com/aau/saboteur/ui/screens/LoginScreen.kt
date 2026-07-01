package com.aau.saboteur.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aau.saboteur.R
import com.aau.saboteur.ui.components.LobbyMenu
import com.aau.saboteur.ui.components.MenuButton
import com.aau.saboteur.ui.components.SpielregelnDialog

@Composable
private fun localizeLoginError(code: String): String = when (code) {
    "error.connection_failed" -> stringResource(R.string.error_connection_failed)
    "error.login_failed"      -> stringResource(R.string.error_login_failed)
    "error.guest_name_taken"  -> stringResource(R.string.error_guest_name_taken)
    else                      -> code
}

@Composable
fun LoginScreen(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onAuthClick: (String, String?, Boolean) -> Unit = { _, _, _ -> }
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var showSpielregeln by remember { mutableStateOf(false) }

    val trimmedUsername = username.trim()
    val isUsernameValid = trimmedUsername.length >= 3
    val isPasswordValid = password.isEmpty() || password.length >= 6
    val canSubmit = isUsernameValid && isPasswordValid && !isLoading

    val isGuestAttempt = trimmedUsername.isNotBlank() && password.isBlank()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_pickaxe),
                    contentDescription = stringResource(R.string.saboteur_logo_desc),
                    modifier = Modifier
                        .size(120.dp)
                        .padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                if (errorMessage != null) {
                    Text(
                        text = localizeLoginError(errorMessage),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading,
                    isError = errorMessage != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading,
                    isError = errorMessage != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
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
                        val buttonLabel = if (isGuestAttempt) R.string.guest_join_button else R.string.login_button
                        Text(text = stringResource(buttonLabel))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                MenuButton(isOpen = menuOpen, onToggle = { menuOpen = !menuOpen })
                LobbyMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    volume = volume,
                    onVolumeChange = { volume = it },
                    showLeaveGame = false,
                    onShowSpielregeln = { showSpielregeln = true }
                )
            }

            if (showSpielregeln) {
                SpielregelnDialog(onDismiss = { showSpielregeln = false })
            }
        }
    }
}
