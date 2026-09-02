package com.postpci.drrrp.ui.staff

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.data.model.UserRole
import com.postpci.drrrp.ui.messaging.MessagingScreen
import com.postpci.drrrp.ui.staff.caregiver.AddCaregiverScreen
import com.postpci.drrrp.ui.staff.dashboard.PatientDetailScreen
import com.postpci.drrrp.ui.staff.dashboard.StaffDashboardScreen
import com.postpci.drrrp.ui.staff.wizard.BaselineWizardScreen
import kotlinx.coroutines.launch

private sealed interface StaffScreen {
    data object Dashboard : StaffScreen
    data class PatientDetail(val patientId: String) : StaffScreen
    data class Wizard(val patientId: String?) : StaffScreen
    data class Messaging(val patientId: String) : StaffScreen
    data class AddCaregiver(val patientId: String) : StaffScreen
}

/** Simple in-shell navigation (no NavHost) across the staff-only screens for this stage. */
@Composable
fun StaffShell(application: DrRrpApplication) {
    var screen by remember { mutableStateOf<StaffScreen>(StaffScreen.Dashboard) }
    val scope = rememberCoroutineScope()
    val currentUser by application.authGateway.currentUser.collectAsState()

    // This shell uses local state rather than a NavHost, so the hardware back button needs its
    // own handler per screen — otherwise it falls through to the Activity default and exits the
    // app instead of navigating up, breaking "hardware back button supported everywhere".
    BackHandler(enabled = screen !is StaffScreen.Dashboard) {
        screen = when (val s = screen) {
            is StaffScreen.Dashboard -> StaffScreen.Dashboard
            is StaffScreen.PatientDetail -> StaffScreen.Dashboard
            is StaffScreen.Wizard -> s.patientId?.let { StaffScreen.PatientDetail(it) } ?: StaffScreen.Dashboard
            is StaffScreen.Messaging -> StaffScreen.PatientDetail(s.patientId)
            is StaffScreen.AddCaregiver -> StaffScreen.PatientDetail(s.patientId)
        }
    }

    when (val s = screen) {
        is StaffScreen.Dashboard -> StaffDashboardScreen(
            application = application,
            onSignOut = { scope.launch { application.authGateway.signOut() } },
            onAddPatient = { screen = StaffScreen.Wizard(null) },
            onOpenPatient = { screen = StaffScreen.PatientDetail(it) },
        )
        is StaffScreen.PatientDetail -> PatientDetailScreen(
            application = application,
            patientId = s.patientId,
            onBack = { screen = StaffScreen.Dashboard },
            onEditBaseline = { screen = StaffScreen.Wizard(s.patientId) },
            onSendMessage = { screen = StaffScreen.Messaging(s.patientId) },
            onAddCaregiver = { screen = StaffScreen.AddCaregiver(s.patientId) },
        )
        is StaffScreen.Wizard -> BaselineWizardScreen(
            application = application,
            patientId = s.patientId,
            onBack = { screen = s.patientId?.let { StaffScreen.PatientDetail(it) } ?: StaffScreen.Dashboard },
            onComplete = { newId -> screen = StaffScreen.PatientDetail(newId) },
        )
        is StaffScreen.Messaging -> MessagingScreen(
            application = application,
            patientId = s.patientId,
            currentUserRole = UserRole.STAFF,
            currentUserId = currentUser?.uid.orEmpty(),
            currentUserName = currentUser?.displayName ?: "Clinic Staff",
            onBack = { screen = StaffScreen.PatientDetail(s.patientId) },
        )
        is StaffScreen.AddCaregiver -> AddCaregiverScreen(
            application = application,
            patientId = s.patientId,
            onBack = { screen = StaffScreen.PatientDetail(s.patientId) },
            onDone = { screen = StaffScreen.PatientDetail(s.patientId) },
        )
    }
}
