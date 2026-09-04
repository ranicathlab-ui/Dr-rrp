package com.postpci.drrrp.ui.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import com.postpci.drrrp.ui.common.bringIntoViewOnFocus
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.data.alert.ClinicContact
import com.postpci.drrrp.data.local.entity.MessageEntity
import com.postpci.drrrp.data.model.UserRole
import com.postpci.drrrp.ui.common.DrRrpScaffold
import com.postpci.drrrp.ui.common.drrrpFieldColors
import com.postpci.drrrp.ui.theme.AccentYellowGold
import com.postpci.drrrp.ui.theme.HeaderBrightBlue
import com.postpci.drrrp.ui.theme.StatusGoodGreen
import com.postpci.drrrp.ui.theme.SurfaceCard
import com.postpci.drrrp.ui.theme.TextPrimary
import com.postpci.drrrp.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a")

/**
 * Single thread per patient — reached from the clinic dashboard ("Send message") on the staff
 * side, and from a small inbox entry point on Today (patient/caregiver side).
 */
@Composable
fun MessagingScreen(
    application: DrRrpApplication,
    patientId: String,
    currentUserRole: UserRole,
    currentUserId: String,
    currentUserName: String,
    onBack: () -> Unit,
    // Defaults to patientId (fine for PatientCaregiverShell — one fixed patient all session).
    // StaffShell passes a fresh-per-visit UUID instead, since it opens this screen for whichever
    // patient staff is currently viewing and has no NavHost to scope a fresh ViewModel per visit
    // automatically — see StaffScreen's doc.
    viewModelKey: String = patientId,
) {
    val viewModel: MessagingViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer { MessagingViewModel(application.messagingRepository, application.syncManager, patientId, currentUserRole, currentUserId, currentUserName) }
        },
    )
    val messages by viewModel.messages.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    DrRrpScaffold(title = "Messages", showBackButton = true, onBack = onBack) { modifier ->
        Column(modifier = modifier.fillMaxSize().imePadding()) {
            if (messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No messages yet — say hello.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message, isMine = message.senderRole == currentUserRole && message.senderId == currentUserId)
                    }
                }
            }

            // Sets expectations so patients don't treat this thread as an emergency line — see
            // the Terms & Conditions' "Doctor response time" section for the same commitment.
            if (currentUserRole != UserRole.STAFF) {
                Text(
                    "Typical clinic response: 4–12 hours during working hours. For emergencies, " +
                        "${ClinicContact.CONTACT_LABEL} directly — don't wait for a reply here.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Type a message…") },
                    colors = drrrpFieldColors(),
                    modifier = Modifier.weight(1f).bringIntoViewOnFocus(),
                )
                IconButton(onClick = { viewModel.send(draft); draft = "" }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = AccentYellowGold)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, isMine: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(if (isMine) HeaderBrightBlue.copy(alpha = 0.35f) else SurfaceCard, RoundedCornerShape(14.dp))
                .padding(12.dp),
        ) {
            if (!isMine) {
                Text(message.senderName, color = AccentYellowGold, style = MaterialTheme.typography.labelMedium)
            }
            Text(message.text, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            val isUnread = (!isMine) && (!message.readByPatient || !message.readByStaff)
            val timestampColor = if (isUnread) StatusGoodGreen else Color(0xFF64748B)
            Text(
                Instant.ofEpochMilli(message.timestamp).atZone(ZoneId.systemDefault()).format(timeFormatter),
                color = timestampColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
