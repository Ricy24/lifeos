package com.example.andresfinanzas.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.SharedPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val authApi: com.example.andresfinanzas.data.remote.api.AuthApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        val token = sharedPreferences.getString("auth_token", null)
        if (!token.isNullOrEmpty() && token != "mock_token_123") {
            _uiState.value = _uiState.value.copy(isAuthenticated = true)
        } else {
            // Clear invalid mock token
            sharedPreferences.edit().remove("auth_token").apply()
            _uiState.value = _uiState.value.copy(isAuthenticated = false)
        }
    }

    fun login(email: String, pin: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = authApi.login(
                    com.example.andresfinanzas.data.remote.api.LoginRequest(
                        email = email.trim(),
                        password = pin.trim()
                    )
                )
                sharedPreferences.edit().putString("auth_token", response.access_token).apply()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al iniciar sesión: ${e.localizedMessage ?: "Credenciales inválidas"}"
                )
            }
        }
    }
}
