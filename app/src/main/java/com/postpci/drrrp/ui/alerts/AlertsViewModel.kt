package com.postpci.drrrp.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpci.drrrp.data.local.entity.AlertEntity
import com.postpci.drrrp.data.repository.PatientCareRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlertsViewModel(private val repository: PatientCareRepository, patientId: String) : ViewModel() {
    val alerts: StateFlow<List<AlertEntity>> =
        repository.observeAllAlerts(patientId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markReviewed(alertId: String) {
        viewModelScope.launch { repository.markAlertReviewed(alertId) }
    }
}
