package com.postpci.drrrp.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpci.drrrp.data.auth.AuthGateway
import com.postpci.drrrp.data.auth.AuthOpResult
import com.postpci.drrrp.data.auth.SignInResult
import kotlinx.coroutines.launch

enum class LoginMode { SIGN_IN, FIRST_LOGIN_SETUP }

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val mode: LoginMode = LoginMode.SIGN_IN,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Backs the Login/Signup screen: plain sign-in for staff, and the "staff-issued invite → patient
 * or caregiver sets their own password" first-login flow (see [AuthGateway]). Successful auth
 * just updates [AuthGateway.currentUser]; the nav host reacts to that, so this ViewModel doesn't
 * need to know where to navigate.
 */
class LoginViewModel(private val authGateway: AuthGateway) : ViewModel() {
    var uiState by mutableStateOf(LoginUiState())
        private set

    fun onEmailChange(value: String) {
        uiState = uiState.copy(email = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, errorMessage = null)
    }

    fun onNewPasswordChange(value: String) {
        uiState = uiState.copy(newPassword = value, errorMessage = null)
    }

    fun onConfirmPasswordChange(value: String) {
        uiState = uiState.copy(confirmPassword = value, errorMessage = null)
    }

    fun submitSignIn() {
        val email = uiState.email.trim()
        val password = uiState.password
        if (email.isEmpty() || password.isEmpty()) {
            uiState = uiState.copy(errorMessage = "Enter your email and password.")
            return
        }
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = authGateway.signIn(email, password)) {
                is SignInResult.Success -> {
                    uiState = uiState.copy(isLoading = false)
                }
                is SignInResult.NeedsPasswordSetup -> {
                    uiState = uiState.copy(isLoading = false, mode = LoginMode.FIRST_LOGIN_SETUP)
                }
                is SignInResult.Error -> {
                    uiState = uiState.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun submitFirstLoginSetup() {
        if (uiState.newPassword.length < 6) {
            uiState = uiState.copy(errorMessage = "Password must be at least 6 characters.")
            return
        }
        if (uiState.newPassword != uiState.confirmPassword) {
            uiState = uiState.copy(errorMessage = "Passwords don't match.")
            return
        }
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authGateway.completeFirstLogin(uiState.email.trim(), uiState.newPassword)
            uiState = when (result) {
                AuthOpResult.Success -> uiState.copy(isLoading = false)
                is AuthOpResult.Error -> uiState.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun backToSignIn() {
        uiState = uiState.copy(mode = LoginMode.SIGN_IN, errorMessage = null)
    }
}
