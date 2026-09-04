package com.postpci.drrrp.ui.staff.dashboard

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.postpci.drrrp.ui.theme.AccentAmber
import com.postpci.drrrp.ui.theme.AlertRoseRed
import com.postpci.drrrp.ui.theme.IBMPlexMono
import com.postpci.drrrp.ui.theme.StatusGoodGreen
import com.postpci.drrrp.ui.theme.StatusInfo
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
            BadgedBox(
                badge = {
                    if (state.totalUnreadCount > 0) {
                        Badge(
                            containerColor = AlertRoseRed,
                            contentColor = Color.White,
                        ) {
                            Text(if (state.totalUnreadCount > 99) "99+" else state.totalUnreadCount.toString())
                        }
                    }
                },
            ) {
                IconButton(onClick = { viewModel.onFilterChange(AlertStatusFilter.MESSAGES) }) {
                    Icon(Icons.Filled.Email, contentDescription = "Messages", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            TextButton(onClick = { scope.launch { application.authGateway.signOut() } }) {
                Text("Sign out", color = MaterialTheme.colorScheme.onSurface)
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
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White),
                    )
                }
            }
            Button(
                onClick = onAddPatient,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("+ Add new patient", fontWeight = FontWeight.Bold) }

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
            title = { Text("Delete Patient Record?", color = AlertRoseRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to permanently delete this patient record? This action cannot be undone.",
                    color = MaterialTheme.colorScheme.onSurface,
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
                    Text("Confirm Delete", color = AlertRoseRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { patientToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
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
    val isEmergency = patient.lastAlertSeverity == AlertSeverity.EMERGENCY
    val borderColor = if (isEmergency) AlertRoseRed else MaterialTheme.colorScheme.outline

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            // Top Row: Name & Day N Badge
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = patient.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                patient.dayNPostPci?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "Day $it",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IBMPlexMono,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Age ${patient.age ?: "—"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Status Badges with Subtle Tinted Backgrounds
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val (badgeText, badgeBg, badgeFg) = when (patient.lastAlertSeverity) {
                    AlertSeverity.EMERGENCY -> Triple("EMERGENCY", AlertRoseRed.copy(alpha = 0.15f), AlertRoseRed)
                    AlertSeverity.ROUTINE -> Triple("Flagged", AccentAmber.copy(alpha = 0.15f), AccentAmber)
                    AlertSeverity.INFO, null -> Triple("Stable", StatusGoodGreen.copy(alpha = 0.15f), StatusGoodGreen)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg,
                ) {
                    Text(
                        text = "● $badgeText",
                        color = badgeFg,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (patient.hasMissedEntry) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = StatusInfo.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "Missed entry",
                            color = StatusInfo,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (patient.hasUnreadMessages || patient.hasMessages) {
                    val unread = patient.hasUnreadMessages
                    val badgeBg = if (unread) StatusGoodGreen.copy(alpha = 0.15f) else Color(0xFF64748B).copy(alpha = 0.12f)
                    val badgeFg = if (unread) StatusGoodGreen else Color(0xFF64748B)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBg,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (unread) "✉ New Message" else "✉ Messages",
                                color = badgeFg,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (unread && patient.unreadCount > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = AlertRoseRed,
                                ) {
                                    Text(
                                        text = if (patient.unreadCount > 99) "99+" else patient.unreadCount.toString(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onOpenMessaging,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text("Message ✉", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete patient record",
                        tint = AlertRoseRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
