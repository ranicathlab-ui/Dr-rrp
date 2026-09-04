package com.postpci.drrrp.ui.staff.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpci.drrrp.data.local.DrRrpDatabase
import com.postpci.drrrp.data.local.entity.AlertEntity
import com.postpci.drrrp.data.local.entity.PatientBaselineEntity
import com.postpci.drrrp.data.model.AlertSeverity
import com.postpci.drrrp.data.schedule.MonitoringSchedule
import com.postpci.drrrp.data.sync.SyncApiService
import com.postpci.drrrp.data.sync.SyncManager
import com.postpci.drrrp.data.sync.dto.PatientListItemDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PatientSummary(
    val patientId: String,
    val name: String,
    val age: Int?,
    val dayNPostPci: Int?,
    val lastAlertSeverity: AlertSeverity?,
    val lastAlertAt: Long?,
    /**
     * True when the patient has fields due today but their most recent daily entry is from
     * yesterday or earlier (or doesn't exist). Computed server-side (GET /staff/patients) when
     * that succeeds; approximated from local Room the same way when it doesn't — see
     * [StaffDashboardViewModel]'s offline fallback.
     */
    val hasMissedEntry: Boolean,
    val hasMessages: Boolean = false,
    val hasUnreadMessages: Boolean = false,
    val unreadCount: Int = 0,
)

enum class AlertStatusFilter { ALL, EMERGENCY, ROUTINE, MESSAGES, NONE }

data class StaffDashboardUiState(
    val isLoading: Boolean = true,
    val patients: List<PatientSummary> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: AlertStatusFilter = AlertStatusFilter.ALL,
    /** True when [remotePatients] failed and the list below is the local-only fallback — staff
     *  should know they might not be seeing every patient (only ones this device has synced). */
    val isOffline: Boolean = false,
)

/**
 * Every patient, for staff — sourced from `GET /staff/patients` (see functions/index.js), which
 * is what actually makes "staff sees every patient's data" true across devices (a patient's own
 * entries live on the patient's device until synced; see the step-6 architecture note in
 * FIREBASE_INTEGRATION.md). Falls back to whatever's in local Room — patients this staff device
 * has itself created/viewed before — when the network call fails, so the dashboard degrades
 * gracefully offline rather than going blank.
 */
class StaffDashboardViewModel(
    private val database: DrRrpDatabase,
    private val syncApiService: SyncApiService,
    private val syncManager: SyncManager,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val statusFilter = MutableStateFlow(AlertStatusFilter.ALL)

    /** null = not loaded yet, or the last attempt failed — triggers the local-Room fallback below. */
    private val remotePatients = MutableStateFlow<List<PatientListItemDto>?>(null)
    private val isLoading = MutableStateFlow(true)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading.value = true
            remotePatients.value = try {
                syncApiService.getStaffPatients()
            } catch (e: Exception) {
                null
            }
            isLoading.value = false
        }
    }

    fun deletePatient(patientId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                syncManager.deletePatient(patientId)
            } catch (_: Exception) {
                // Best-effort offline delete
            }
            refresh()
            onSuccess()
        }
    }

    val uiState: StateFlow<StaffDashboardUiState> = combine(
        remotePatients,
        database.patientBaselineDao().observeAll(),
        database.alertDao().observeMostRecentPerPatient(),
        database.messageDao().observePatientIdsWithMessages(),
        database.messageDao().observePatientIdsWithUnreadForStaff(),
        searchQuery,
        statusFilter,
    ) { flows: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val remote = flows[0] as List<PatientListItemDto>?
        @Suppress("UNCHECKED_CAST")
        val baselines = flows[1] as List<PatientBaselineEntity>
        @Suppress("UNCHECKED_CAST")
        val recentAlerts = flows[2] as List<AlertEntity>
        @Suppress("UNCHECKED_CAST")
        val messagePatientIds = flows[3] as List<String>
        @Suppress("UNCHECKED_CAST")
        val unreadPatientIds = flows[4] as List<String>
        val query = flows[5] as String
        val filter = flows[6] as AlertStatusFilter

        val today = LocalDate.now()
        val messageIdsSet = messagePatientIds.toSet()
        val unreadIdsSet = unreadPatientIds.toSet()

        val summaries = if (remote != null) {
            remote.map { item ->
                PatientSummary(
                    patientId = item.patientId,
                    name = item.name,
                    age = item.age,
                    dayNPostPci = item.pciDate?.let { MonitoringSchedule.daysPostPci(LocalDate.parse(it), today) },
                    lastAlertSeverity = item.lastAlertSeverity?.let { AlertSeverity.valueOf(it) },
                    lastAlertAt = item.lastAlertAt,
                    hasMissedEntry = item.hasMissedEntry,
                    hasMessages = item.patientId in messageIdsSet,
                    hasUnreadMessages = item.patientId in unreadIdsSet,
                )
            }
        } else {
            // Offline fallback: the same derivation this screen used before the REST backend
            // existed — local baselines only, so this may well be an incomplete list.
            val alertByPatient = recentAlerts.associateBy { it.patientId }
            baselines.map { baseline ->
                val pciDate = baseline.procedural.pciDate
                val latestEntry = database.dailyEntryDao().getLatestForPatient(baseline.patientId)
                val dueFieldsToday = pciDate?.let { MonitoringSchedule.dueFieldsFor(it, today) }.orEmpty()
                val daysSinceLastEntry = latestEntry?.entryDate?.let { ChronoUnit.DAYS.between(it, today) } ?: Long.MAX_VALUE
                val alert = alertByPatient[baseline.patientId]
                PatientSummary(
                    patientId = baseline.patientId,
                    name = baseline.demographics.name,
                    age = baseline.demographics.age,
                    dayNPostPci = pciDate?.let { MonitoringSchedule.daysPostPci(it, today) },
                    lastAlertSeverity = alert?.severity,
                    lastAlertAt = alert?.createdAt,
                    hasMissedEntry = dueFieldsToday.isNotEmpty() && daysSinceLastEntry >= 1,
                    hasMessages = baseline.patientId in messageIdsSet,
                    hasUnreadMessages = baseline.patientId in unreadIdsSet,
                )
            }
        }

        val filtered = summaries
            .filter { it.name.contains(query, ignoreCase = true) }
            .filter { s ->
                when (filter) {
                    AlertStatusFilter.ALL -> true
                    AlertStatusFilter.EMERGENCY -> s.lastAlertSeverity == AlertSeverity.EMERGENCY
                    AlertStatusFilter.ROUTINE -> s.lastAlertSeverity == AlertSeverity.ROUTINE
                    AlertStatusFilter.MESSAGES -> s.hasMessages || s.hasUnreadMessages
                    AlertStatusFilter.NONE -> s.lastAlertSeverity == null
                }
            }
            // Sorted by most recent flag, per spec; patients with no alert at all sort last.
            .sortedByDescending { it.lastAlertAt ?: -1L }

        StaffDashboardUiState(isLoading = false, patients = filtered, searchQuery = query, statusFilter = filter, isOffline = remote == null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StaffDashboardUiState())

    fun onSearchChange(value: String) {
        searchQuery.value = value
    }

    fun onFilterChange(value: AlertStatusFilter) {
        statusFilter.value = value
    }
}
