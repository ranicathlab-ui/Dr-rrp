package com.postpci.drrrp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpci.drrrp.data.model.AlertSeverity
import com.postpci.drrrp.data.repository.PatientCareRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Watches for unreviewed EMERGENCY-tier alerts across the whole patient/caregiver shell (not
 * just the Today tab) so the full-screen escalation interrupts Trends/Alerts/Profile too, not
 * just whichever tab happened to trigger it. A dismissed alert stays dismissed for this app
 * session but remains unreviewed until staff/patient actually reviews it on the Alerts screen.
 */
class EmergencyGateViewModel(repository: PatientCareRepository, patientId: String) : ViewModel() {
    private val dismissedIds = MutableStateFlow<Set<String>>(emptySet())

    val pendingEmergencyAlert: StateFlow<com.postpci.drrrp.data.local.entity.AlertEntity?> = combine(
        repository.observeUnreviewedAlerts(patientId),
        dismissedIds,
    ) { alerts, dismissed ->
        alerts
            .filter { it.severity == AlertSeverity.EMERGENCY }
            .filterNot { it.id in dismissed }
            .maxByOrNull { it.createdAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismiss(alertId: String) {
        dismissedIds.value = dismissedIds.value + alertId
    }
}
