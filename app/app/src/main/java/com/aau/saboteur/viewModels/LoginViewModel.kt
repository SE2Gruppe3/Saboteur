package com.aau.saboteur.viewModels

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aau.saboteur.data.repository.AuthRepository
import com.aau.saboteur.model.User
import kotlinx.coroutines.launch
import java.io.IOException

class LoginViewModel(private val repository: AuthRepository = AuthRepository()) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun login(username: String, password: String?, onSuccess: (User) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            val result = repository.loginUser(username, password)

            isLoading = false

            result.onSuccess { user ->
                onSuccess(user)
            }.onFailure { e ->
                errorMessage = when (e) {
                    is IOException -> "error.connection_failed"
                    else -> e.message?.takeIf { it.startsWith("error.") } ?: "error.login_failed"
                }
            }
        }
    }
}
