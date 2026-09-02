package com.postpci.drrrp.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.data.alert.ClinicContact
import com.postpci.drrrp.data.alert.LegalLinks
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.AlertRed
import com.postpci.drrrp.ui.theme.BorderHairline
import com.postpci.drrrp.ui.theme.SurfaceCard
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary
import com.postpci.drrrp.ui.theme.appBackground

/**
 * Mandatory notice shown once per device, before sign-in is ever reachable — required by Google
 * Play's health-app review: patients must explicitly acknowledge this is a monitoring tool, not
 * an automated emergency response system, before they can use the app. Gated behind
 * [com.postpci.drrrp.data.onboarding.DisclaimerPreferences]; see [com.postpci.drrrp.ui.navigation.DrRrpNavHost]
 * for how it's wired as the actual nav-graph start destination.
 */
@Composable
fun DisclaimerScreen(application: DrRrpApplication, onAcknowledged: () -> Unit) {
    val context = LocalContext.current
    var checked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .appBackground()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = "Before you continue",
            style = MaterialTheme.typography.headlineMedium,
            color = AccentYellowGold,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "DR RRP — Aasai Health Centre, Salem",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        NoticeCard(title = "What this app is") {
            Text(
                "DR RRP is a monitoring tool that helps your care team follow your post-procedure " +
                    "recovery — it facilitates follow-up between visits. It does not provide a " +
                    "diagnosis and does not replace in-person clinical evaluation or independent " +
                    "emergency triage.",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        NoticeCard(title = "In an emergency", accent = AlertRed) {
            Text(
                "This app is not an automated real-time emergency response system. If you " +
                    "experience severe chest pain, extreme breathlessness, sudden cold sweats, or " +
                    "loss of consciousness, do not rely solely on an in-app message.",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                "${ClinicContact.CONTACT_LABEL} directly at ${ClinicContact.PHONE_NUMBER} " +
                    "and proceed immediately to the hospital.",
                color = AlertRed,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { checked = it },
                colors = CheckboxDefaults.colors(checkedColor = AccentYellowGold, checkmarkColor = Color(0xFF241A00)),
            )
            Text(
                "I understand DR RRP is a monitoring tool, not an automated emergency response " +
                    "system, and I agree to the Privacy Policy and Terms & Conditions.",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Row(modifier = Modifier.padding(top = 4.dp)) {
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.PRIVACY_POLICY_URL))) }) {
                Text("Privacy Policy", color = AccentYellowGold, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.TERMS_URL))) }) {
                Text("Terms & Conditions", color = AccentYellowGold, style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            onClick = {
                application.disclaimerPreferences.hasAcknowledged = true
                onAcknowledged()
            },
            enabled = checked,
            colors = ButtonDefaults.buttonColors(containerColor = AccentYellowGold, contentColor = Color(0xFF241A00)),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) { Text("I understand, continue", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun NoticeCard(title: String, accent: Color = AccentYellowGold, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(if (accent == AlertRed) AlertRed.copy(alpha = 0.10f) else SurfaceCard, RoundedCornerShape(16.dp))
            .border(1.dp, if (accent == AlertRed) AlertRed else BorderHairline, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(title, color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}
