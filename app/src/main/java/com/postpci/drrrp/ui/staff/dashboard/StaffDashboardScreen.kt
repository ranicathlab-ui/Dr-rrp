package com.postpci.drrrp.ui.staff.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.data.model.AlertSeverity
import com.postpci.drrrp.ui.common.DrRrpScaffold
import com.postpci.drrrp.ui.common.drrrpFieldColors
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.AlertRed
import com.postpci.drrrp.ui.theme.BorderHairline
import com.postpci.drrrp.ui.theme.IBMPlexMono
import com.postpci.drrrp.ui.theme.StatusInfo
import com.postpci.drrrp.ui.theme.SurfaceCard
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Patient list sorted by most recent flag, with search + alert-status filter, per spec. */
@Composable
fun StaffDashboardScreen(
    application: DrRrpApplication,
    onSignOut: () -> Unit,
    onAddPatient: () -> Unit,
    onOpenPatient: (String) -> Unit,
    onOpenMessaging: (String) -> Unit = {},
) {
    val viewModel: StaffDashboardViewModel = viewModel(
        factory = viewModelFactory { initializer { StaffDashboardViewModel(application.database, application.syncApiService) } },
    )
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScopeCompat()

    DrRrpScaffold(
        title = "Clinic Dashboard",
        actions = {
            TextButton(onClick = { scope.launch { application.authGateway.signOut() } }) {
                Text("Sign out", color = TextPrimary)
            }
        },
    ) { modifier ->
        Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchChange,
                label = { Text("Search patients") },
                singleLine = true,
                colors = drrrpFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertStatusFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.statusFilter == filter,
                        onClick = { viewModel.onFilterChange(filter) },
                        label = { Text(filter.name.lowercase().replaceFirstChar(Char::uppercase)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentYellowGold, selectedLabelColor = Color(0xFF241A00)),
                    )
                }
            }
            Button(
                onClick = onAddPatient,
                colors = ButtonDefaults.buttonColors(containerColor = AccentYellowGold, contentColor = Color(0xFF241A00)),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
            ) { Text("+ Add new patient") }

            if (state.isOffline) {
                Text(
                    "Showing locally cached patients only — check your connection to see everyone.",
                    color = StatusInfo,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (state.patients.isEmpty() && !state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No patients match.", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn {
                    items(state.patients, key = { it.patientId }) { patient ->
                        PatientCard(
                            patient = patient,
                            onClick = { onOpenPatient(patient.patientId) },
                            onOpenMessaging = { onOpenMessaging(patient.patientId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()

@Composable
private fun PatientCard(
    patient: PatientSummary,
    onClick: () -> Unit,
    onOpenMessaging: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .border(1.dp, if (patient.lastAlertSeverity == AlertSeverity.EMERGENCY) AlertRed else BorderHairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(patient.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            patient.dayNPostPci?.let {
                Text("Day $it", color = AccentYellowGold, style = MaterialTheme.typography.labelLarge, fontFamily = IBMPlexMono)
            }
        }
        Text(
            "Age ${patient.age ?: "—"}",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                when (patient.lastAlertSeverity) {
                    AlertSeverity.EMERGENCY -> Text("● EMERGENCY", color = AlertRed, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    AlertSeverity.ROUTINE -> Text("● Flagged", color = AlertRed, style = MaterialTheme.typography.labelLarge)
                    AlertSeverity.INFO, null -> Text("No active alerts", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
                if (patient.hasMissedEntry) {
                    Text(
                        "  ·  Missed entry",
                        color = StatusInfo,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (patient.hasUnreadMessages) {
                    Text(
                        "  ·  ✉ New msg",
                        color = AccentYellowGold,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                } else if (patient.hasMessages) {
                    Text(
                        "  ·  ✉ Messages",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            TextButton(
                onClick = onOpenMessaging,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("Message ✉", color = AccentYellowGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
