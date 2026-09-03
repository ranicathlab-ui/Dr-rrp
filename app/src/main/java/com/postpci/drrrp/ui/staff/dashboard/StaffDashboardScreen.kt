package com.postpci.drrrp.ui.staff.dashboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.data.model.AlertSeverity
import com.postpci.drrrp.ui.common.DrRrpScaffold
import com.postpci.drrrp.ui.common.bringIntoViewOnFocus
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
        factory = viewModelFactory { initializer { StaffDashboardViewModel(application.database, application.syncApiService, application.syncManager) } },
    )
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScopeCompat()
    val context = LocalContext.current
    var patientToDelete by remember { mutableStateOf<PatientSummary?>(null) }

    DrRrpScaffold(
        title = "Clinic Dashboard",
        actions = {
            TextButton(onClick = { scope.launch { application.authGateway.signOut() } }) {
                Text("Sign out", color = TextPrimary)
            }
        },
    ) { modifier ->
        Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp).imePadding()) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchChange,
                label = { Text("Search patients") },
                singleLine = true,
                colors = drrrpFieldColors(),
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
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
                        val isMessagesFilter = state.statusFilter == AlertStatusFilter.MESSAGES
                        PatientCard(
                            patient = patient,
                            onClick = {
                                if (isMessagesFilter) {
                                    onOpenMessaging(patient.patientId)
                                } else {
                                    onOpenPatient(patient.patientId)
                                }
                            },
                            onOpenMessaging = { onOpenMessaging(patient.patientId) },
                            onDelete = { patientToDelete = patient },
                        )
                    }
                }
            }
        }
    }

    patientToDelete?.let { patient ->
        AlertDialog(
            onDismissRequest = { patientToDelete = null },
            title = { Text("Delete Patient Record?", color = AlertRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to permanently delete this patient record? This action cannot be undone.",
                    color = TextPrimary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = patient.patientId
                        patientToDelete = null
                        viewModel.deletePatient(id) {
                            Toast.makeText(context, "Patient profile deleted successfully.", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text("Confirm Delete", color = AlertRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { patientToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
        )
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()

@Composable
private fun PatientCard(
    patient: PatientSummary,
    onClick: () -> Unit,
    onOpenMessaging: () -> Unit,
    onDelete: () -> Unit,
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
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = patient.name,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            patient.dayNPostPci?.let {
                Text(
                    text = "Day $it",
                    color = AccentYellowGold,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = IBMPlexMono,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Text(
            text = "Age ${patient.age ?: "—"}",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        // Status badges arranged cleanly to prevent overlapping
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (patient.lastAlertSeverity) {
                    AlertSeverity.EMERGENCY -> Text("● EMERGENCY", color = AlertRed, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    AlertSeverity.ROUTINE -> Text("● Flagged", color = AlertRed, style = MaterialTheme.typography.labelLarge)
                    AlertSeverity.INFO, null -> Text("No active alerts", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
                if (patient.hasMissedEntry) {
                    Text("· Missed entry", color = StatusInfo, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (patient.hasUnreadMessages || patient.hasMessages) {
                Text(
                    text = if (patient.hasUnreadMessages) "✉ New message(s)" else "✉ Messages thread",
                    color = if (patient.hasUnreadMessages) AccentYellowGold else TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (patient.hasUnreadMessages) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        // Action buttons aligned cleanly at the bottom right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onOpenMessaging,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentYellowGold),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentYellowGold),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("Message ✉", color = AccentYellowGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete patient record", tint = AlertRed)
            }
        }
    }
}
