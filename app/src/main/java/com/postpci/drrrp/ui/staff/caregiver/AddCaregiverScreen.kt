package com.postpci.drrrp.ui.staff.caregiver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.ui.common.DrRrpScaffold
import com.postpci.drrrp.ui.common.FormPasswordField
import com.postpci.drrrp.ui.common.FormTextField
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.AlertRed
import com.postpci.drrrp.ui.theme.SurfaceCard
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary

/**
 * Staff-only: link a new caregiver account to [patientId] — the counterpart of the baseline
 * wizard's patient-invite step (see its `InviteCredentialsCard`), same "name + contact in, temp
 * credentials to hand over out" shape via [AuthGateway.createCaregiverInvite][
 * com.postpci.drrrp.data.auth.AuthGateway.createCaregiverInvite].
 */
@Composable
fun AddCaregiverScreen(
    application: DrRrpApplication,
    patientId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    // Defaults to patientId; StaffShell passes a fresh-per-visit UUID instead so adding a
    // caregiver for one patient, then another, never reuses a stale ViewModel still holding the
    // first patient's id — see StaffScreen's doc.
    viewModelKey: String = patientId,
) {
    val viewModel: AddCaregiverViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory { initializer { AddCaregiverViewModel(application.authGateway, patientId) } },
    )

    DrRrpScaffold(title = "Add caregiver", showBackButton = true, onBack = onBack) { modifier ->
        // verticalScroll + imePadding: with five fields (name, contact, email, password, confirm)
        // plus the submit button, this content can be taller than the screen even before the
        // keyboard shows up — see FormFields.kt's bringIntoViewOnFocus doc for the rest of the
        // keyboard-avoidance story each field below gets for free.
        Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp).verticalScroll(rememberScrollState()).imePadding()) {
            val credentials = viewModel.inviteCredentials
            if (credentials != null) {
                CaregiverInviteCredentialsCard(email = credentials.email, tempPassword = credentials.temporaryPassword, emailSent = credentials.emailSent)
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentYellowGold, contentColor = Color(0xFF241A00)),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Done") }
                return@DrRrpScaffold
            }

            FormTextField("Caregiver name", viewModel.name) { viewModel.onNameChange(it) }
            FormTextField("Contact number", viewModel.contact) { viewModel.onContactChange(it) }
            FormTextField(
                "Email (optional — leave blank if the caregiver has no email)",
                viewModel.email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            ) { viewModel.onEmailChange(it) }
            FormPasswordField("Create password", viewModel.password, onValueChange = viewModel::onPasswordChange)
            FormPasswordField("Confirm password", viewModel.confirmPassword, onValueChange = viewModel::onConfirmPasswordChange)
            viewModel.errorMessage?.let {
                Text(it, color = AlertRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            Button(
                onClick = viewModel::createInvite,
                enabled = !viewModel.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = AccentYellowGold, contentColor = Color(0xFF241A00)),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text(if (viewModel.isSaving) "Creating…" else "Create caregiver invite") }
        }
    }
}

@Composable
private fun CaregiverInviteCredentialsCard(email: String, tempPassword: String, emailSent: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .border(1.dp, AccentYellowGold, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("Caregiver invite created", color = AccentYellowGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (emailSent) {
                "A password-setup email was sent to $email — the caregiver can use that link directly. " +
                    "The temporary password below is a fallback if the email doesn't arrive."
            } else {
                "Share these with the caregiver — they'll set their own password on first login."
            },
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Text("Email: $email", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        Text("Temporary password: $tempPassword", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}
