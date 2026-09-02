package com.postpci.drrrp.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.ui.common.DrRrpScaffold
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Patient's own Today screen. */
@Composable
fun PatientHome(application: DrRrpApplication) {
    val user by application.authGateway.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    val uid = user?.uid ?: return
    PatientCaregiverShell(
        application = application,
        patientId = uid,
        loggedByCaregiver = false,
        onSignOut = { scope.launch { application.authGateway.signOut() } },
    )
}

/** Caregiver sees the same Today screen for their linked patient; entries get flagged as caregiver-logged. */
@Composable
fun CaregiverHome(application: DrRrpApplication) {
    val user by application.authGateway.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    val linkedPatientId = user?.linkedPatientId

    if (linkedPatientId == null) {
        DrRrpScaffold(
            title = "Today",
            actions = { TextButton(onClick = { scope.launch { application.authGateway.signOut() } }) { Text("Sign out", color = TextPrimary) } },
        ) { modifier ->
            Column(modifier = modifier.padding(24.dp)) {
                Text("No patient is linked to this caregiver account yet.", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    PatientCaregiverShell(
        application = application,
        patientId = linkedPatientId,
        loggedByCaregiver = true,
        canLogEntries = user?.canLogEntries ?: true,
        onSignOut = { scope.launch { application.authGateway.signOut() } },
    )
}
