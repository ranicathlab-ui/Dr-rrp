package com.postpci.drrrp.ui.staff.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.postpci.drrrp.data.schedule.MonitoringSchedule
import com.postpci.drrrp.ui.common.DrRrpScaffold
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.BorderHairline
import com.postpci.drrrp.ui.theme.IBMPlexMono
import com.postpci.drrrp.ui.theme.SurfaceCard
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary
import java.time.LocalDate

/** Baseline summary + paginated daily-log history, with "Call patient" / "Send message" actions. */
@Composable
fun PatientDetailScreen(
    application: DrRrpApplication,
    patientId: String,
    onBack: () -> Unit,
    onEditBaseline: () -> Unit,
    onSendMessage: () -> Unit,
    onAddCaregiver: () -> Unit,
    // Defaults to patientId for any other caller; StaffShell passes a fresh-per-visit UUID
    // instead — see StaffScreen's doc for why that matters (no NavHost here to scope this
    // automatically, so two different patients viewed in the same app session would otherwise
    // share one cached ViewModel instance and show one patient's data on the other's screen).
    viewModelKey: String = patientId,
) {
    val viewModel: PatientDetailViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory { initializer { PatientDetailViewModel(application.database, application.syncManager, patientId) } },
    )
    val baseline by viewModel.baseline.collectAsState()
    val context = LocalContext.current

    DrRrpScaffold(title = baseline?.demographics?.name ?: "Patient", showBackButton = true, onBack = onBack) { modifier ->
        val b = baseline
        if (b == null) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentYellowGold) }
            return@DrRrpScaffold
        }

        LazyColumn(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            item {
                val dayN = b.procedural.pciDate?.let { MonitoringSchedule.daysPostPci(it, LocalDate.now()) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .background(SurfaceCard, RoundedCornerShape(16.dp))
                        .border(1.dp, BorderHairline, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Text(b.demographics.name, color = TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Age ${b.demographics.age ?: "—"} · ${b.demographics.sex?.name ?: "—"}" + (dayN?.let { " · Day $it post-PCI" } ?: ""),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "PCI: ${b.procedural.pciDate ?: "not set"}  ·  Culprit: ${b.procedural.culpritVessel?.name ?: "—"}  ·  LVEF: ${b.labsAndVitals.lvefPercent ?: "—"}%",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${b.demographics.contactNumber}")))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentYellowGold, contentColor = Color(0xFF241A00)),
                        ) { Text("Call patient") }
                        OutlinedButton(onClick = onSendMessage) { Text("Send message", color = TextPrimary) }
                    }
                    OutlinedButton(onClick = onEditBaseline, modifier = Modifier.padding(top = 10.dp)) {
                        Text("Edit baseline", color = TextPrimary)
                    }
                    OutlinedButton(onClick = onAddCaregiver, modifier = Modifier.padding(top = 10.dp)) {
                        Text("Add caregiver", color = TextPrimary)
                    }
                }

                Text(
                    "Daily log history",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            items(viewModel.entries, key = { it.id }) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(SurfaceCard, RoundedCornerShape(14.dp))
                        .border(1.dp, BorderHairline, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Text(entry.entryDate.toString(), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "HR ${entry.restingHeartRate ?: "—"}  BP ${entry.bpSystolic ?: "—"}/${entry.bpDiastolic ?: "—"}  " +
                            "SpO2 ${entry.spo2 ?: "—"}  Wt ${entry.weightKg ?: "—"}kg",
                        color = TextPrimary,
                        fontFamily = IBMPlexMono,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (entry.loggedByCaregiver) {
                        Text("Logged by caregiver", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            item {
                if (viewModel.isInitialLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentYellowGold)
                    }
                } else if (viewModel.entries.isEmpty()) {
                    Text("No daily entries logged yet.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                } else if (viewModel.hasMore) {
                    Button(
                        onClick = viewModel::loadNextPage,
                        enabled = !viewModel.isLoadingPage,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = AccentYellowGold),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) { Text(if (viewModel.isLoadingPage) "Loading…" else "Load more") }
                }
            }
        }
    }
}
