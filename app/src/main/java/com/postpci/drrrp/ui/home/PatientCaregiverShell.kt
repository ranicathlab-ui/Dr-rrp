package com.postpci.drrrp.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.ui.alerts.AlertsScreen
import com.postpci.drrrp.ui.messaging.MessagingScreen
import com.postpci.drrrp.ui.profile.ProfileScreen
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.HeaderBrightBlue
import com.postpci.drrrp.ui.theme.HeaderDeepBlue
import com.postpci.drrrp.ui.theme.TextSecondary
import com.postpci.drrrp.ui.today.EmergencyAlertScreen
import com.postpci.drrrp.ui.today.TodayScreen
import com.postpci.drrrp.ui.trends.TrendsScreen

private enum class BottomTab(val label: String) {
    TODAY("Today"), TRENDS("Trends"), ALERTS("Alerts"), PROFILE("Profile")
}

/**
 * Shared shell for the patient's own view and the caregiver's (read-mostly, per spec) view of
 * the same patient: bottom nav across Today/Trends/Alerts/Profile. Caregiver entries just carry
 * [loggedByCaregiver] = true through to Today's Log Entry flow.
 *
 * A pending emergency alert is checked here, above the tab content, so the full-screen
 * escalation truly takes over the whole screen — including hiding the bottom nav — rather than
 * just replacing whichever tab happened to be open. It interrupts every tab, not only Today.
 */
@Composable
fun PatientCaregiverShell(
    application: DrRrpApplication,
    patientId: String,
    loggedByCaregiver: Boolean,
    onSignOut: () -> Unit,
    /** False only for a caregiver whose account has logging disabled (see AuthUser.canLogEntries)
     *  — always true for a patient viewing their own data. */
    canLogEntries: Boolean = true,
) {
    var selectedTab by remember { mutableStateOf(BottomTab.TODAY) }
    var showMessages by remember { mutableStateOf(false) }
    val currentUser by application.authGateway.currentUser.collectAsState()

    // Keyed by class + patientId, not patientId alone: androidx's ViewModelStore maps a `key`
    // string directly to a ViewModel instance with no per-class namespacing, so if two different
    // ViewModel classes on this same store (e.g. this and TodayViewModel/TrendsViewModel/
    // AlertsViewModel, all below) ever requested `viewModel(key = patientId, ...)` with the bare
    // patientId, the second call's store.put() would silently evict *and clear* whichever
    // ViewModel got there first — which is exactly what was happening here: this ViewModel was
    // being constructed, then torn down within the same composition pass the moment TodayScreen's
    // own `viewModel(key = patientId, ...)` ran, so its alert Flow never got the chance to emit
    // and the full-screen emergency takeover could never appear. See sibling screens for the same
    // "ClassName:patientId" pattern.
    val emergencyGate: EmergencyGateViewModel = viewModel(
        key = "EmergencyGate:$patientId",
        factory = viewModelFactory {
            initializer { EmergencyGateViewModel(application.patientCareRepository, patientId) }
        },
    )
    val pendingEmergencyAlert by emergencyGate.pendingEmergencyAlert.collectAsState()
    pendingEmergencyAlert?.let { emergency ->
        EmergencyAlertScreen(alert = emergency) { emergencyGate.dismiss(emergency.id) }
        return
    }

    // Same reasoning as StaffShell: this overlay isn't on a NavHost back stack, so without this
    // handler hardware back would exit the app instead of closing the overlay.
    BackHandler(enabled = showMessages) { showMessages = false }

    if (showMessages) {
        val user = currentUser
        MessagingScreen(
            application = application,
            patientId = patientId,
            currentUserRole = user?.role ?: com.postpci.drrrp.data.model.UserRole.PATIENT,
            currentUserId = user?.uid.orEmpty(),
            currentUserName = user?.displayName ?: "You",
            onBack = { showMessages = false },
        )
        return
    }

    Scaffold(
        containerColor = com.postpci.drrrp.ui.theme.BackgroundNearBlack,
        bottomBar = {
            NavigationBar(containerColor = HeaderDeepBlue) {
                BottomTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(iconFor(tab), contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentYellowGold,
                            selectedTextColor = AccentYellowGold,
                            indicatorColor = HeaderBrightBlue.copy(alpha = 0.35f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
            when (selectedTab) {
                BottomTab.TODAY -> TodayScreen(application, patientId, loggedByCaregiver, canLogEntries, onSignOut) { showMessages = true }
                BottomTab.TRENDS -> TrendsScreen(application, patientId)
                BottomTab.ALERTS -> AlertsScreen(application, patientId)
                BottomTab.PROFILE -> ProfileScreen(application, patientId)
            }
        }
    }
}

private fun iconFor(tab: BottomTab) = when (tab) {
    BottomTab.TODAY -> Icons.Filled.Home
    BottomTab.TRENDS -> Icons.Filled.DateRange
    BottomTab.ALERTS -> Icons.Filled.Notifications
    BottomTab.PROFILE -> Icons.Filled.Person
}
