package com.example.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.LocalUser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: LocalAuthRepository
) : ViewModel() {

    val currentUser: StateFlow<LocalUser?> = repository.currentUser

    private val _isRestoringSession = MutableStateFlow(true)
    val isRestoringSession: StateFlow<Boolean> = _isRestoringSession.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _authEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val authEvents: SharedFlow<String> = _authEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.restoreSession()
            _isRestoringSession.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun signIn(username: String, password: String, onSuccess: () -> Unit) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            val result = repository.signIn(username, password)
            _isSubmitting.value = false
            result.fold(
                onSuccess = { user ->
                    _authEvents.tryEmit("Signed in as ${user.username}")
                    onSuccess()
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Sign in failed"
                }
            )
        }
    }

    fun createAccount(
        username: String,
        password: String,
        confirmPassword: String,
        onSuccess: () -> Unit
    ) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            val result = repository.signUp(username, password, confirmPassword)
            _isSubmitting.value = false
            result.fold(
                onSuccess = { user ->
                    _authEvents.tryEmit("Account created & signed in as ${user.username}")
                    onSuccess()
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Account creation failed"
                }
            )
        }
    }

    fun signOut() {
        _errorMessage.value = null
        viewModelScope.launch {
            repository.signOut()
            _authEvents.tryEmit("Signed out")
        }
    }
}
