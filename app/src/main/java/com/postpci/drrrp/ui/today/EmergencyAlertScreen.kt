package com.postpci.drrrp.ui.today

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.postpci.drrrp.data.alert.ClinicContact
import com.postpci.drrrp.data.local.entity.AlertEntity
import com.postpci.drrrp.ui.theme.AlertRed
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary

/**
 * Distinct, more urgent full-screen alert for syncope, unrelieved rest chest pain, a bleeding
 * event needing medical attention, or NYHA class IV — deliberately not the same UI as a routine
 * flag. A single action, [ClinicContact.CONTACT_LABEL], dials the same clinic number as every
 * other contact action in the app; it's styled as the sole, visually primary button here (no
 * second action to balance it against) rather than because it dials anywhere different.
 */
@Composable
fun EmergencyAlertScreen(alert: AlertEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlertRed.copy(alpha = 0.14f))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = AlertRed, modifier = Modifier.padding(bottom = 16.dp))
        Text(
            text = "This looks like an emergency",
            style = MaterialTheme.typography.headlineMedium,
            color = AlertRed,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = alert.message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        Text(
            text = "Contact Dr. Rajaram Prasad immediately.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        Button(
            onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ClinicContact.PHONE_NUMBER}"))) },
            colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(ClinicContact.CONTACT_LABEL, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 16.dp)) {
            Text("I've contacted help / dismiss", color = TextSecondary)
        }
    }
}
