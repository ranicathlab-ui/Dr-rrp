package com.postpci.drrrp.ui.alerts

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.postpci.drrrp.data.local.entity.AlertEntity
import com.postpci.drrrp.data.model.AlertSeverity
import com.postpci.drrrp.ui.common.DrRrpScaffold
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.AlertRed
import com.postpci.drrrp.ui.theme.BorderHairline
import com.postpci.drrrp.ui.theme.StatusInfo
import com.postpci.drrrp.ui.theme.SurfaceCard
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a")

/** Chronological alert history — flagged readings and events, "Mark as reviewed" per item. */
@Composable
fun AlertsScreen(application: DrRrpApplication, patientId: String) {
    val viewModel: AlertsViewModel = viewModel(
        // Namespaced by class, not bare patientId — see EmergencyGateViewModel's comment on
        // ViewModelStore key collisions across sibling ViewModels sharing the same store.
        key = "Alerts:$patientId",
        factory = viewModelFactory {
            initializer { AlertsViewModel(application.patientCareRepository, patientId) }
        },
    )
    val alerts by viewModel.alerts.collectAsState()

    DrRrpScaffold(title = "Alerts") { modifier ->
        if (alerts.isEmpty()) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No alerts yet — readings in range stay quiet.", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
            return@DrRrpScaffold
        }
        LazyColumn(modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            items(alerts, key = { it.id }) { alert ->
                AlertCard(alert, onMarkReviewed = { viewModel.markReviewed(alert.id) })
            }
        }
    }
}

@Composable
private fun AlertCard(alert: AlertEntity, onMarkReviewed: () -> Unit) {
    val (accent, label) = when (alert.severity) {
        AlertSeverity.EMERGENCY -> AlertRed to "EMERGENCY"
        AlertSeverity.ROUTINE -> AlertRed to "FLAGGED"
        AlertSeverity.INFO -> StatusInfo to "INFO"
    }
    val isEmergency = alert.severity == AlertSeverity.EMERGENCY

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(if (isEmergency) AlertRed.copy(alpha = 0.10f) else SurfaceCard, RoundedCornerShape(16.dp))
            .border(if (isEmergency) 2.dp else 1.dp, if (alert.reviewed) BorderHairline else accent, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                text = Instant.ofEpochMilli(alert.createdAt).atZone(ZoneId.systemDefault()).format(dateTimeFormatter),
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            alert.message,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        alert.normalRangeText?.let {
            Text("Normal range: $it", color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (alert.reviewed) {
                Text("Reviewed ✓", color = AccentYellowGold, style = MaterialTheme.typography.labelLarge)
            } else {
                Button(
                    onClick = onMarkReviewed,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentYellowGold, contentColor = Color(0xFF241A00)),
                ) {
                    Text("Mark as reviewed")
                }
            }
        }
    }
}
