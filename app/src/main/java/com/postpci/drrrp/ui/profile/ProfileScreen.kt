package com.postpci.drrrp.ui.profile

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.postpci.drrrp.ui.onboarding.LegalTextDialog
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.data.alert.ClinicContact
import com.postpci.drrrp.data.alert.LegalLinks
import com.postpci.drrrp.data.local.entity.PatientBaselineEntity
import com.postpci.drrrp.ui.common.DrRrpScaffold
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.AlertRed
import com.postpci.drrrp.ui.theme.BorderHairline
import com.postpci.drrrp.ui.theme.SurfaceCard
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Read-only baseline view for patients/caregivers — editing is staff-only (see the Stage 6
 * baseline wizard). Emergency contact (the clinic) is kept prominent near the top per spec.
 */
@Composable
fun ProfileScreen(application: DrRrpApplication, patientId: String) {
    val baseline by application.database.patientBaselineDao().observe(patientId).collectAsState(initial = null)
    val context = LocalContext.current

    DrRrpScaffold(title = "Profile") { modifier ->
        val b = baseline
        if (b == null) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentYellowGold)
            }
            return@DrRrpScaffold
        }

        LazyColumn(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            item {
                EmergencyContactCard {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ClinicContact.PHONE_NUMBER}")))
                }
            }
            item { SectionCard("Demographics") { DemographicsRows(b) } }
            item { SectionCard("Procedural details") { ProceduralRows(b) } }
            item { SectionCard("Labs & vitals at discharge") { LabsRows(b) } }
            item { SectionCard("Medications & follow-up") { MedsRows(b) } }
            item { SectionCard("Social") { SocialRows(b) } }
            item {
                SectionCard("Patient Education") {
                    Text(
                        "Watch stent care, diet, and cardiac recovery guidance videos by Dr. A. Rajaram Prasad, Cardiologist.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.YOUTUBE_CHANNEL_URL)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Watch Videos on YouTube ▶", fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { LegalAndDataSection(application, patientId) }
        }
    }
}

/** Privacy Policy / Terms & Conditions links plus the account & data deletion request required by
 * Google Play's Data Safety section — see LegalLinks' doc for why these are hosted pages rather
 * than in-app text. */
@Composable
private fun LegalAndDataSection(application: DrRrpApplication, patientId: String) {
    val currentUser by application.authGateway.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var requestSent by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    SectionCard("Legal") {
        TextButton(onClick = { showPrivacyDialog = true }) {
            Text("Privacy Policy", color = AccentYellowGold)
        }
        TextButton(onClick = { showTermsDialog = true }) {
            Text("Terms & Conditions", color = AccentYellowGold)
        }
    }

    if (showPrivacyDialog) {
        LegalTextDialog(
            title = "Privacy Policy",
            text = LegalLinks.PRIVACY_POLICY_TEXT,
            url = LegalLinks.PRIVACY_POLICY_URL,
            onDismiss = { showPrivacyDialog = false },
        )
    }

    if (showTermsDialog) {
        LegalTextDialog(
            title = "Terms & Conditions",
            text = LegalLinks.TERMS_TEXT,
            url = LegalLinks.TERMS_URL,
            onDismiss = { showTermsDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .border(1.dp, BorderHairline, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("Your data", color = AccentYellowGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "You can request complete deletion of your account and health data at any time.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        if (requestSent) {
            Text(
                "Request sent — the clinic will process this within a few days. You can also call them directly.",
                color = AccentYellowGold,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            OutlinedButton(
                onClick = { showConfirmDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed),
            ) { Text("Request account & data deletion") }
        }
    }

    if (showConfirmDialog) {
        val user = currentUser
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Delete your account and data?") },
            text = {
                Text(
                    "This sends a request to Aasai Health Centre to permanently delete your DR RRP " +
                        "account and all your logged health data. This can take a few days to process " +
                        "— you'll be contacted once it's done. You can also call the clinic directly " +
                        "instead of using this button.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    if (user != null) {
                        scope.launch {
                            try {
                                application.messagingRepository.send(
                                    patientId,
                                    user.role,
                                    user.uid,
                                    user.displayName,
                                    "ACCOUNT DELETION REQUEST — this patient has requested complete deletion of their account and health data, per the app's Privacy Policy.",
                                )
                                requestSent = true
                            } catch (e: Exception) {
                                // Best-effort — if this fails to send, the confirmation dialog's own
                                // text already told the user they can call the clinic directly instead.
                            }
                        }
                    }
                }) { Text("Send request", color = AlertRed) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmergencyContactCard(onCall: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(AlertRed.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .border(1.dp, AlertRed, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("Emergency contact", color = AlertRed, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Aasai Health Centre, Salem", color = TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
        Text(ClinicContact.PHONE_NUMBER, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = onCall,
            colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = Color.White),
            modifier = Modifier.padding(top = 12.dp),
        ) { Text(ClinicContact.CONTACT_LABEL) }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .border(1.dp, BorderHairline, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(title, color = AccentYellowGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Column(modifier = Modifier.padding(top = 8.dp)) { content() }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
        Text(value, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DemographicsRows(b: PatientBaselineEntity) {
    val d = b.demographics
    InfoRow("Name", d.name)
    InfoRow("Age", d.age?.toString())
    InfoRow("Sex", d.sex?.name)
    InfoRow("Contact number", d.contactNumber)
    InfoRow("Email", d.email)
    InfoRow("Comorbidities", d.comorbidities)
    InfoRow("Current medications", d.currentMedications)
}

@Composable
private fun ProceduralRows(b: PatientBaselineEntity) {
    val p = b.procedural
    InfoRow("PCI date", p.pciDate?.toString())
    InfoRow("STEMI territory", p.stemiTerritory?.name)
    InfoRow("Culprit vessel", p.culpritVessel?.name)
    InfoRow("Access site", p.accessSite?.name)
    InfoRow("Stent count", p.stentCount?.toString())
    InfoRow("Stent type", p.stentType)
    InfoRow("Non-culprit disease", if (p.nonCulpritDiseasePresent) "Yes" else "No")
    InfoRow("Staged PCI date", p.stagedPciDate?.toString())
}

@Composable
private fun LabsRows(b: PatientBaselineEntity) {
    val l = b.labsAndVitals
    InfoRow("LVEF", l.lvefPercent?.let { "$it%" })
    InfoRow("Killip class", l.killipClass?.name)
    InfoRow("Discharge BP", if (l.dischargeSystolicBp != null) "${l.dischargeSystolicBp}/${l.dischargeDiastolicBp}" else null)
    InfoRow("Discharge weight", l.dischargeWeightKg?.let { "$it kg" })
    InfoRow("LDL-C", l.ldlC?.toString())
    InfoRow("HbA1c", l.hba1c?.toString())
}

@Composable
private fun MedsRows(b: PatientBaselineEntity) {
    val m = b.medicationsAndFollowUp
    InfoRow("DAPT", m.daptAgent)
    InfoRow("DAPT duration", m.daptPlannedDurationMonths?.let { "$it months" })
    InfoRow("Beta-blocker", m.betaBlockerDose)
    InfoRow("ACEi / ARB / ARNI", m.aceiArbArniDose)
    InfoRow("MRA", m.mraDose)
    InfoRow("SGLT2i", m.sglt2iDose)
    InfoRow("Cardiac rehab referred", if (m.cardiacRehabReferred) "Yes" else "No")
    InfoRow("OPD follow-up", m.opdFollowUpDate?.toString())
    InfoRow("Echo follow-up", m.echoFollowUpDate?.toString())
    InfoRow("Lipid recheck", m.lipidRecheckDate?.toString())
}

@Composable
private fun SocialRows(b: PatientBaselineEntity) {
    val s = b.social
    InfoRow("Smoking status", s.smokingStatus?.name)
    InfoRow("Occupation", s.occupation)
    InfoRow("Return to work", s.expectedReturnToWorkDate?.toString())
    InfoRow("Lives alone", s.livesAlone?.let { if (it) "Yes" else "No" })
    InfoRow("Has caregiver", s.hasCaregiver?.let { if (it) "Yes" else "No" })
    InfoRow("Preferred language", s.preferredLanguage?.name)
}
