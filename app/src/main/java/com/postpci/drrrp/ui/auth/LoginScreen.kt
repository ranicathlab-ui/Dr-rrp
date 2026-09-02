package com.postpci.drrrp.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.AlertRed
import com.postpci.drrrp.ui.theme.BorderHairline
import com.postpci.drrrp.ui.theme.SurfaceCard
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary
import com.postpci.drrrp.ui.theme.appBackground

/** Which half of the sign-in screen is showing — purely presentational: both lead to the exact
 *  same [LoginViewModel.submitSignIn], since the account's real role always comes from its
 *  Firestore doc, never from which tab was tapped. This only exists so patients/caregivers and
 *  clinical staff each see copy addressed to them, instead of one form trying to speak to both. */
private enum class LoginAudience { PATIENT, STAFF }

@Composable
fun LoginScreen(application: DrRrpApplication, modifier: Modifier = Modifier) {
    val viewModel: LoginViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LoginViewModel(application.authGateway) }
        },
    )
    val state = viewModel.uiState
    var audience by remember { mutableStateOf(LoginAudience.PATIENT) }

    Column(
        modifier = modifier
            .appBackground()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Dr. A. Rajaram Prasad",
            style = MaterialTheme.typography.headlineMedium,
            color = AccentYellowGold,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Recovery monitoring for Aasai Health Centre, Salem",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 28.dp, top = 4.dp),
        )

        if (state.mode == LoginMode.SIGN_IN) {
            AudienceSwitch(selected = audience, onSelect = { audience = it })
        }

        when (state.mode) {
            LoginMode.SIGN_IN -> SignInForm(viewModel, state.email, state.password, state.isLoading, audience)
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

/** Two-segment toggle, styled like the rest of the app's pill filter chips (see
 *  StaffDashboardScreen's category filter) but full-width and bigger — this is the first choice
 *  on the screen, so it reads as a primary control, not an incidental filter. */
@Composable
private fun AudienceSwitch(selected: LoginAudience, onSelect: (LoginAudience) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .background(SurfaceCard, RoundedCornerShape(14.dp))
            .border(1.dp, BorderHairline, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AudienceSegment("Patients", selected == LoginAudience.PATIENT, Modifier.weight(1f)) { onSelect(LoginAudience.PATIENT) }
        AudienceSegment("Clinical Staff", selected == LoginAudience.STAFF, Modifier.weight(1f)) { onSelect(LoginAudience.STAFF) }
    }
}

@Composable
private fun AudienceSegment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (selected) AccentYellowGold else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) androidx.compose.ui.graphics.Color(0xFF241A00) else TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SignInForm(viewModel: LoginViewModel, email: String, password: String, isLoading: Boolean, audience: LoginAudience) {
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
        text = if (audience == LoginAudience.PATIENT) {
            "Patients and caregivers: sign in with the email and temporary password " +
                "the clinic gave you — you'll set your own password on first login."
        } else {
            "Clinical staff: sign in with your clinic-issued credentials."
        },
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
