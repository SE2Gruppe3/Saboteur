package com.aau.saboteur.viewModels

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aau.saboteur.data.repository.AuthRepository
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: AuthRepository = AuthRepository()) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun login(username: String, password: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            val result = repository.loginUser(username, password)

            isLoading = false

            result.onSuccess {
                onSuccess()
            }.onFailure {
                // Hier werden Netzwerkfehler oder falsche Daten abgefangen
                errorMessage = it.message ?: "Login fehlgeschlagen"
            }
        }
    }
}