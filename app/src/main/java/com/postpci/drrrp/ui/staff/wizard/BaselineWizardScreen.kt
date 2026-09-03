package com.postpci.drrrp.ui.staff.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.ui.common.DrRrpScaffold
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.AlertRed
import com.postpci.drrrp.ui.theme.BorderHairline
import com.postpci.drrrp.ui.theme.SurfaceCard
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary

/**
 * Multi-step wizard, sectioned exactly along Demographics → Procedural → Labs & Vitals →
 * Medications & Follow-up → Social. Each step saves to Room on "Next" (see
 * [BaselineWizardViewModel]), so staff can back out and resume later from wherever they left off.
 *
 * [patientId] null means "new patient" — the invite is minted the moment staff leaves step 0,
 * and its credentials are shown once so staff can hand them to the patient.
 */
@Composable
fun BaselineWizardScreen(
    application: DrRrpApplication,
    patientId: String?,
    onBack: () -> Unit,
    onComplete: (String) -> Unit,
    // Defaults to patientId, but that's null for every "new patient" wizard — every such
    // instance would otherwise share the one cached ViewModel (no NavHost to scope this
    // automatically), so a second "Add new patient" mid-session would silently resume the
    // *first* patient's in-progress draft instead of starting fresh. StaffShell passes a
    // fresh-per-visit UUID instead; see StaffScreen's doc.
    viewModelKey: String = patientId ?: "new-patient",
) {
    val viewModel: BaselineWizardViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer {
                BaselineWizardViewModel(application.database, application.authGateway, patientId) {
                    com.postpci.drrrp.data.sync.SyncScheduler.requestImmediateSync(application)
                }
            }
        },
    )

    DrRrpScaffold(
        title = if (patientId == null) "New patient" else "Edit baseline",
        showBackButton = true,
        onBack = onBack,
    ) { modifier ->
        if (viewModel.isLoading) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentYellowGold)
            }
            return@DrRrpScaffold
        }

        if (viewModel.isComplete) {
            val id = viewModel.patientId
            if (id != null) onComplete(id)
            return@DrRrpScaffold
        }

        val credentials = viewModel.inviteCredentials

        // imePadding: without it, the keyboard covers whatever's at the bottom of the list with
        // no room to scroll a lower field past it — each field's own bringIntoViewOnFocus (see
        // FormFields.kt) needs that extra space to actually scroll into, not just a request to.
        LazyColumn(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp).imePadding()) {
            item {
                StepProgress(currentStep = viewModel.currentStep)
                if (credentials != null && viewModel.currentStep == 1) {
                    InviteCredentialsCard(email = credentials.email, tempPassword = credentials.temporaryPassword, emailSent = credentials.emailSent)
                }
                viewModel.errorMessage?.let {
                    Text(it, color = AlertRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
                }
                when (viewModel.currentStep) {
                    // isNewPatient reads the screen's own `patientId` param (null = new patient),
                    // not viewModel.patientId — that field flips non-null the instant the invite's
                    // created mid-flow, but this needs to stay true for the rest of that same
                    // "new patient" session so the password fields don't disappear right after use.
                    0 -> DemographicsStep(viewModel.draft.demographics, false, isNewPatient = patientId == null, onBack = {}) { demographics, password ->
                        viewModel.updateDemographics(demographics)
                        viewModel.saveCurrentStepAndAdvance(password)
                    }
                    1 -> ProceduralStep(viewModel.draft.procedural, viewModel::goBack) {
                        viewModel.updateProcedural(it)
                        viewModel.saveCurrentStepAndAdvance()
                    }
                    2 -> LabsStep(viewModel.draft.labsAndVitals, viewModel::goBack) {
                        viewModel.updateLabs(it)
                        viewModel.saveCurrentStepAndAdvance()
                    }
                    3 -> MedicationsStep(viewModel.draft.medicationsAndFollowUp, viewModel::goBack) {
                        viewModel.updateMeds(it)
                        viewModel.saveCurrentStepAndAdvance()
                    }
                    4 -> SocialStep(viewModel.draft.social, viewModel::goBack) {
                        viewModel.updateSocial(it)
                        viewModel.saveCurrentStepAndAdvance()
                    }
                }
            }
        }
    }
}

@Composable
private fun StepProgress(currentStep: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WIZARD_STEP_TITLES.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(if (index <= currentStep) AccentYellowGold else BorderHairline, RoundedCornerShape(2.dp)),
            )
        }
    }
    Text(
        "Step ${currentStep + 1} of 5 — ${WIZARD_STEP_TITLES[currentStep]}",
        style = MaterialTheme.typography.titleMedium,
        color = TextPrimary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 16.dp),
    )
}

@Composable
private fun InviteCredentialsCard(email: String, tempPassword: String, emailSent: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .border(1.dp, AccentYellowGold, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("Patient invite created", color = AccentYellowGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (emailSent) {
                "A password-setup email was sent to $email — the patient can use that link directly. " +
                    "The temporary password below is a fallback if the email doesn't arrive."
            } else {
                "Share these with the patient — they'll set their own password on first login."
            },
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Text("Email: $email", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        Text("Temporary password: $tempPassword", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}
