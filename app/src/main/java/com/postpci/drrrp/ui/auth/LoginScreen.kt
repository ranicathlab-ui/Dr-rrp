package com.postpci.drrrp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.AlertRed
import com.postpci.drrrp.ui.theme.BorderHairline
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary
import com.postpci.drrrp.ui.theme.appBackground

@Composable
fun LoginScreen(application: DrRrpApplication, modifier: Modifier = Modifier) {
    val viewModel: LoginViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LoginViewModel(application.authGateway) }
        },
    )
    val state = viewModel.uiState

    Column(
        modifier = modifier
            .appBackground()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "DR RRP",
            style = MaterialTheme.typography.displayLarge,
            color = AccentYellowGold,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Recovery monitoring for Aasai Health Centre, Salem",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 32.dp, top = 4.dp),
        )

        when (state.mode) {
            LoginMode.SIGN_IN -> SignInForm(viewModel, state.email, state.password, state.isLoading)
            LoginMode.FIRST_LOGIN_SETUP -> FirstLoginSetupForm(
                viewModel = viewModel,
                email = state.email,
                newPassword = state.newPassword,
                confirmPassword = state.confirmPassword,
                isLoading = state.isLoading,
            )
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = AlertRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun SignInForm(viewModel: LoginViewModel, email: String, password: String, isLoading: Boolean) {
    OutlinedTextField(
        value = email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        colors = drrrpTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = viewModel::onPasswordChange,
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        colors = drrrpTextFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
    Button(
        onClick = viewModel::submitSignIn,
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(containerColor = AccentYellowGold, contentColor = androidx.compose.ui.graphics.Color(0xFF241A00)),
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
        } else {
            Text("Sign in")
        }
    }
    Text(
        text = "Patients and caregivers: sign in with the email and temporary password " +
            "the clinic gave you — you'll set your own password on first login.",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun FirstLoginSetupForm(
    viewModel: LoginViewModel,
    email: String,
    newPassword: String,
    confirmPassword: String,
    isLoading: Boolean,
) {
    Text(
        text = "Welcome! Set a password for $email to finish signing in.",
        style = MaterialTheme.typography.titleMedium,
        color = TextPrimary,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    OutlinedTextField(
        value = newPassword,
        onValueChange = viewModel::onNewPasswordChange,
        label = { Text("New password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        colors = drrrpTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = confirmPassword,
        onValueChange = viewModel::onConfirmPasswordChange,
        label = { Text("Confirm password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        colors = drrrpTextFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
    Button(
        onClick = viewModel::submitFirstLoginSetup,
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(containerColor = AccentYellowGold, contentColor = androidx.compose.ui.graphics.Color(0xFF241A00)),
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
        } else {
            Text("Set password and continue")
        }
    }
    TextButton(onClick = viewModel::backToSignIn, modifier = Modifier.padding(top = 4.dp)) {
        Text("Back to sign in", color = TextSecondary)
    }
}

@Composable
private fun drrrpTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = AccentYellowGold,
    unfocusedBorderColor = BorderHairline,
    focusedLabelColor = AccentYellowGold,
    unfocusedLabelColor = TextSecondary,
    cursorColor = AccentYellowGold,
)
