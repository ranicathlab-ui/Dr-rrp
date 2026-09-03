package com.postpci.drrrp.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * Shared shell for the patient's own view and the caregiver's view of the same patient.
 */
@Composable
fun PatientCaregiverShell(
    application: DrRrpApplication,
    patientId: String,
    loggedByCaregiver: Boolean,
    onSignOut: () -> Unit,
    canLogEntries: Boolean = true,
) {
    var selectedTab by remember { mutableStateOf(BottomTab.TODAY) }
    var showMessages by remember { mutableStateOf(false) }
    val currentUser by application.authGateway.currentUser.collectAsState()

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
        containerColor = MaterialTheme.colorScheme.background,
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
