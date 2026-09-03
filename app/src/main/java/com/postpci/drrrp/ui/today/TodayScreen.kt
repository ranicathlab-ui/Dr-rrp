package com.postpci.drrrp.ui.today

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.postpci.drrrp.data.local.entity.isAnyFieldLogged
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.postpci.drrrp.data.alert.ClinicContact
import com.postpci.drrrp.data.alert.LegalLinks
import com.postpci.drrrp.data.local.entity.DailyEntryEntity
import com.postpci.drrrp.data.model.AlertSeverity
import com.postpci.drrrp.ui.common.DrRrpScaffold
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.AlertRed
import com.postpci.drrrp.ui.theme.BorderHairline
import com.postpci.drrrp.ui.theme.IBMPlexMono
import com.postpci.drrrp.ui.theme.SurfaceCard
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.data.schedule.MonitoringSchedule
import com.postpci.drrrp.ui.theme.TextSecondary

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox

/**
 * The core loop: greeting + day badge, alert banner, today's due vitals grid, medication
 * checklist (DAPT highlighted), and the finish button. Used for both the patient's own view and
 * the caregiver view — [loggedByCaregiver] just flags whichever entries get saved.
 */
@Composable
fun TodayScreen(
    application: DrRrpApplication,
    patientId: String,
    loggedByCaregiver: Boolean,
    /** False only for a caregiver whose account has logging disabled — see AuthUser.canLogEntries. */
    canLogEntries: Boolean = true,
    onSignOut: () -> Unit,
    onOpenMessages: () -> Unit = {},
) {
    val viewModel: TodayViewModel = viewModel(
        // Namespaced by class, not bare patientId — see EmergencyGateViewModel's comment on
        // ViewModelStore key collisions across sibling ViewModels sharing the same store.
        key = "Today:$patientId",
        factory = viewModelFactory {
            initializer { TodayViewModel(application.database, application.patientCareRepository, application.messagingRepository, application.syncManager, patientId, loggedByCaregiver) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var activeFieldSheet by remember { mutableStateOf<String?>(null) }

    DrRrpScaffold(
        title = "Today",
        actions = {
            BadgedBox(
                badge = {
                    if (state.unreadMessageCount > 0) {
                        Badge(
                            containerColor = AlertRed,
                            contentColor = Color.White,
                        ) {
                            Text(if (state.unreadMessageCount > 99) "99+" else state.unreadMessageCount.toString())
                        }
                    }
                },
            ) {
                IconButton(onClick = onOpenMessages) {
                    Icon(Icons.Filled.Email, contentDescription = "Messages", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            TextButton(onClick = onSignOut) { Text("Sign out", color = MaterialTheme.colorScheme.onSurface) }
        },
    ) { modifier ->
        if (state.isLoading) {
            Box(modifier = modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentYellowGold) }
            return@DrRrpScaffold
        }
        if (!state.hasBaseline) {
            Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Your clinic hasn't set up your recovery profile yet. Please contact Aasai Health Centre.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@DrRrpScaffold
        }

        // verticalScroll here is load-bearing: the due-fields grid, medications checklist, and
        // "Finish today's check-in" button together can easily exceed one screen (5+ due fields
        // is normal), and without this the button was rendering entirely off-screen with no way
        // to reach it — the "no submit button" bug. See LogEntrySheet's own scroll fix for the
        // sibling case (the per-field entry sheet, once a value's typed and the keyboard is up).
        Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp).verticalScroll(rememberScrollState())) {
            GreetingHeader(state)

            if (!canLogEntries) {
                Text(
                    "This caregiver account is read-only — ask the clinic to enable logging if you need to record entries.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            val routineAlerts = state.unreviewedAlerts.filter { it.severity == AlertSeverity.ROUTINE }
            AnimatedVisibility(
                visible = routineAlerts.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                if (routineAlerts.isNotEmpty()) {
                    AlertBanner(count = routineAlerts.size, latestMessage = routineAlerts.first().message) {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ClinicContact.PHONE_NUMBER}")))
                    }
                }
            }

            val isMandatoryMet = state.todayEntry?.isAnyFieldLogged() == true

            // Mandatory Daily Compliance Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .background(
                        if (isMandatoryMet) AccentYellowGold.copy(alpha = 0.12f) else AlertRed.copy(alpha = 0.10f),
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        1.dp,
                        if (isMandatoryMet) AccentYellowGold else AlertRed,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(14.dp),
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (isMandatoryMet) "Daily Compliance: Completed ✓" else "Daily Compliance: Pending ✗",
                            color = if (isMandatoryMet) AccentYellowGold else AlertRed,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (isMandatoryMet) {
                            Text("Mandatory Met", color = AccentYellowGold, style = MaterialTheme.typography.labelSmall, fontFamily = IBMPlexMono)
                        }
                    }
                    Text(
                        text = if (isMandatoryMet) {
                            "You have completed your mandatory check-in for today. You can log additional follow-up vitals at any time."
                        } else {
                            "At least one daily vital sign check-in is required today. Please log your vitals."
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Text(
                "Today's check-in",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
            )
            // Not a LazyVerticalGrid: this Column already scrolls (see above), and nesting one
            // scrollable inside another needs unbounded-height gymnastics for no real benefit —
            // due fields are a short, fixed-size list (under ten), never a long feed. A plain
            // chunked 2-column layout avoids that entirely.
            // Medications get their own checklist section below, not a generic grid card.
            state.dueFields.filter { it != MonitoringSchedule.MEDICATIONS_TAKEN }.chunked(2).forEach { rowFields ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    rowFields.forEach { fieldKey ->
                        Box(modifier = Modifier.weight(1f)) {
                            DueFieldCard(fieldKey, state.todayEntry, enabled = canLogEntries) { activeFieldSheet = fieldKey }
                        }
                    }
                    if (rowFields.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }

            if (state.medications.isNotEmpty()) {
                MedicationChecklist(state, viewModel, enabled = canLogEntries)
            }

            if (canLogEntries) {
                var finished by remember { mutableStateOf(false) }
                var showSuccessDialog by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isMandatoryMet) {
                        Button(
                            onClick = {
                                Toast.makeText(context, "Select any vital card above to log additional vitals.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard, contentColor = AccentYellowGold),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("+ Log Additional Vitals", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            Toast.makeText(context, "Vitals recorded successfully", Toast.LENGTH_SHORT).show()
                            viewModel.finishCheckIn {
                                showSuccessDialog = true
                                finished = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentYellowGold, contentColor = Color(0xFF241A00)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isMandatoryMet) "Submit Follow-up Entry" else "Finish today's check-in", fontWeight = FontWeight.Bold)
                    }
                }

                if (showSuccessDialog) {
                    val unloggedFields = state.dueFields.filter { !isFieldLogged(it, state.todayEntry) }
                    AlertDialog(
                        onDismissRequest = { showSuccessDialog = false },
                        title = {
                            Text(
                                text = "Vitals Recorded Successfully ✓",
                                color = AccentYellowGold,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        text = {
                            if (unloggedFields.isEmpty()) {
                                Text(
                                    "All done for today! Your vital signs and daily check-in details have been recorded and submitted to Aasai Health Centre.",
                                    color = TextPrimary,
                                )
                            } else {
                                val unloggedLabels = unloggedFields.map { fieldMetaByKey[it]?.label ?: it }.joinToString(", ")
                                Text(
                                    "Your vitals have been recorded and submitted to Aasai Health Centre.\n\nNote: ${unloggedFields.size} field(s) still pending today: $unloggedLabels\n\nSubsequent follow-up entries later in the day are optional.",
                                    color = TextPrimary,
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showSuccessDialog = false }) {
                                Text("Done", color = AccentYellowGold, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = SurfaceCard,
                    )
                }

                AnimatedVisibility(
                    visible = finished || isMandatoryMet,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(AccentYellowGold.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .border(1.dp, AccentYellowGold, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = "✓ Vitals recorded successfully — mandatory daily check-in met!",
                            color = AccentYellowGold,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            YouTubeGuidanceCard {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.YOUTUBE_CHANNEL_URL)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    if (canLogEntries) {
        activeFieldSheet?.let { fieldKey ->
            LogEntrySheet(fieldKey, state.todayEntry, viewModel) { activeFieldSheet = null }
        }
    }
}

@Composable
private fun YouTubeGuidanceCard(onOpenYouTube: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp)
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .border(1.dp, BorderHairline, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("Doctor's Video Guidance", color = AccentYellowGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Watch stent care, diet, and recovery guidance videos by Dr. A. Rajaram Prasad, Cardiologist",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Button(
            onClick = onOpenYouTube,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Watch Videos on YouTube ▶", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GreetingHeader(state: TodayUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Hi, ${state.patientName.ifBlank { "there" }}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Text("Aasai Health Centre, Salem", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.dayNPostPci?.let { day ->
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Day $day post-PCI", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontFamily = IBMPlexMono)
            }
        }
    }
}

@Composable
private fun AlertBanner(count: Int, latestMessage: String, onCallClinic: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(AlertRed.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .border(1.dp, AlertRed, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("$count reading(s) need attention", color = AlertRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(latestMessage, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Button(
            onClick = onCallClinic,
            colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = Color.White),
        ) { Text(ClinicContact.CONTACT_LABEL) }
    }
}

@Composable
private fun DueFieldCard(fieldKey: String, entry: DailyEntryEntity?, enabled: Boolean = true, onClick: () -> Unit) {
    val meta = fieldMetaByKey[fieldKey]
    val logged = isFieldLogged(fieldKey, entry)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .border(1.dp, if (logged) AccentYellowGold.copy(alpha = 0.5f) else BorderHairline, RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Text(meta?.label ?: fieldKey, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(meta?.rangeText ?: "", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Bottom) {
            Text(
                text = if (logged) "Logged ✓" else if (enabled) "Tap to log" else "Not logged",
                color = if (logged) AccentYellowGold else TextSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = if (logged) IBMPlexMono else null,
            )
        }
    }
}

private fun isFieldLogged(fieldKey: String, entry: DailyEntryEntity?): Boolean {
    if (entry == null) return false
    return when (fieldKey) {
        MonitoringSchedule.RESTING_HEART_RATE -> entry.restingHeartRate != null
        MonitoringSchedule.BLOOD_PRESSURE -> entry.bpSystolic != null && entry.bpDiastolic != null
        MonitoringSchedule.SPO2 -> entry.spo2 != null
        MonitoringSchedule.WEIGHT -> entry.weightKg != null
        MonitoringSchedule.ACCESS_SITE_CHECK ->
            entry.accessSiteBleeding != null && entry.accessSiteSwelling != null && entry.accessSitePain != null && entry.accessSiteDiscolouration != null
        MonitoringSchedule.CHEST_PAIN -> entry.chestPainCount != null
        MonitoringSchedule.ACTIVITY -> entry.stepsOrMinutesWalked != null
        MonitoringSchedule.PALPITATIONS_SYNCOPE ->
            entry.palpitations != null && entry.syncope != null && entry.nearSyncope != null
        MonitoringSchedule.BREATHLESSNESS -> entry.nyhaClass != null
        MonitoringSchedule.MEDICATIONS_TAKEN -> entry.daptTaken != null || !entry.medicationsTaken.isNullOrBlank()
        else -> false
    }
}

@Composable
private fun MedicationChecklist(state: TodayUiState, viewModel: TodayViewModel, enabled: Boolean = true) {
    var checkedState by remember(state.todayEntry?.id) {
        val taken = state.todayEntry?.medicationsTaken?.split(",")?.filter { it.isNotBlank() }?.toSet().orEmpty()
        mutableStateOf(taken)
    }

    Text("Medications today", style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .border(1.dp, BorderHairline, RoundedCornerShape(16.dp))
            .padding(8.dp),
    ) {
        state.medications.forEach { med ->
            val checked = med.key in checkedState
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (med.isDapt) Modifier.background(AccentYellowGold.copy(alpha = 0.10f), RoundedCornerShape(10.dp)) else Modifier)
                    .padding(6.dp),
            ) {
                Checkbox(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = { isChecked ->
                        val updated = if (isChecked) checkedState + med.key else checkedState - med.key
                        checkedState = updated
                        viewModel.submitMedications(updated.toList(), daptTaken = "dapt" in updated)
                    },
                    colors = CheckboxDefaults.colors(checkedColor = AccentYellowGold, checkmarkColor = Color(0xFF241A00)),
                )
                Text(
                    med.label + if (med.isDapt) " (DAPT)" else "",
                    color = if (med.isDapt) AccentYellowGold else TextPrimary,
                    fontWeight = if (med.isDapt) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
