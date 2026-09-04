package com.postpci.drrrp.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpci.drrrp.data.auth.FakeAuthGateway
import com.postpci.drrrp.data.local.DrRrpDatabase
import com.postpci.drrrp.data.local.entity.Demographics
import com.postpci.drrrp.data.local.entity.LabsAndVitals
import com.postpci.drrrp.data.local.entity.MedicationsAndFollowUp
import com.postpci.drrrp.data.local.entity.PatientBaselineEntity
import com.postpci.drrrp.data.local.entity.ProceduralDetails
import com.postpci.drrrp.data.local.entity.Social
import com.postpci.drrrp.data.model.AccessSite
import com.postpci.drrrp.data.model.ChestPainType
import com.postpci.drrrp.data.model.NyhaClass
import com.postpci.drrrp.data.model.PreferredLanguage
import com.postpci.drrrp.data.model.Sex
import com.postpci.drrrp.data.model.StemiTerritory
import com.postpci.drrrp.data.repository.PatientCareRepository
import com.postpci.drrrp.data.schedule.MonitoringSchedule
import com.postpci.drrrp.data.sync.SyncManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Backs the Today screen — the core loop. Combines the patient's baseline (name, PCI date),
 * today's [com.postpci.drrrp.data.local.entity.DailyEntryEntity], and unreviewed alerts into one
 * state; the due-fields grid comes straight from [MonitoringSchedule] rather than being
 * hardcoded here.
 */
import com.postpci.drrrp.data.repository.MessagingRepository

class TodayViewModel(
    private val database: DrRrpDatabase,
    private val repository: PatientCareRepository,
    private val messagingRepository: MessagingRepository,
    private val syncManager: SyncManager,
    private val patientId: String,
    private val loggedByCaregiver: Boolean,
) : ViewModel() {

    init {
        seedDemoBaselineIfNeeded()
        // Pulls the staff-authored baseline (and any server-side alerts, e.g. from the daily
        // missed-entry check) down to this device — without this, a patient's own phone would
        // never see the baseline staff entered on a different device. Best-effort: offline just
        // means today's screen keeps showing whatever's already local.
        viewModelScope.launch {
            try {
                syncManager.pullPatient(patientId)
            } catch (e: Exception) {
                // Offline or the request failed — nothing to do, local state stands.
            }
        }
    }

    private val _dismissedAlertIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())

    fun dismissAlertBanner(alertId: String) {
        _dismissedAlertIds.value = _dismissedAlertIds.value + alertId
        viewModelScope.launch {
            repository.markAlertReviewed(alertId)
        }
    }

    // Emergency-tier alerts are handled one level up, by EmergencyGateViewModel in the shell —
    // that way a pending emergency interrupts every tab (Trends/Alerts/Profile too), not just
    // Today, and the full-screen escalation truly covers the whole screen (no bottom nav
    // showing underneath it).
    val uiState: StateFlow<TodayUiState> = combine(
        database.patientBaselineDao().observe(patientId),
        repository.observeTodayEntry(patientId),
        repository.observeUnreviewedAlerts(patientId),
        messagingRepository.observeUnreadCountForPatient(patientId),
        _dismissedAlertIds,
    ) { baseline, entry, alerts, unreadCount, dismissedIds ->
        val today = LocalDate.now()
        val pciDate = baseline?.procedural?.pciDate
        val dayN = pciDate?.let { MonitoringSchedule.daysPostPci(it, today) }
        val dueFields = pciDate?.let { MonitoringSchedule.dueFieldsFor(it, today) }.orEmpty()

        val now = System.currentTimeMillis()
        val twentyFourHoursAgo = now - 24 * 60 * 60 * 1000L
        val activeAlerts = alerts
            .filter { it.createdAt >= twentyFourHoursAgo }
            .filter { !dismissedIds.contains(it.id) }

        TodayUiState(
            isLoading = false,
            hasBaseline = baseline != null,
            patientName = baseline?.demographics?.name.orEmpty(),
            pciDate = pciDate,
            dayNPostPci = dayN,
            dueFields = dueFields,
            todayEntry = entry,
            medications = baseline?.let(::medicationsFor).orEmpty(),
            unreviewedAlerts = activeAlerts,
            unreadMessageCount = unreadCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private fun medicationsFor(baseline: PatientBaselineEntity): List<MedItem> {
        val m = baseline.medicationsAndFollowUp
        return buildList {
            add(MedItem("dapt", m.daptAgent?.takeIf { it.isNotBlank() } ?: "DAPT", isDapt = true))
            if (!m.betaBlockerDose.isNullOrBlank()) add(MedItem("betaBlocker", "Beta-blocker"))
            if (!m.aceiArbArniDose.isNullOrBlank()) add(MedItem("aceiArbArni", "ACEi / ARB / ARNI"))
            if (!m.mraDose.isNullOrBlank()) add(MedItem("mra", "MRA"))
            if (!m.sglt2iDose.isNullOrBlank()) add(MedItem("sglt2i", "SGLT2i"))
        }
    }

    fun submitRestingHeartRate(bpm: Int) = launchSave { repository.saveRestingHeartRate(patientId, bpm, loggedByCaregiver) }
    fun submitBloodPressure(systolic: Int, diastolic: Int) = launchSave { repository.saveBloodPressure(patientId, systolic, diastolic, loggedByCaregiver) }
    fun submitSpo2(percent: Int) = launchSave { repository.saveSpo2(patientId, percent, loggedByCaregiver) }
    fun submitWeight(kg: Double) = launchSave { repository.saveWeight(patientId, kg, loggedByCaregiver) }
    fun submitAccessSite(bleeding: Boolean, swelling: Boolean, pain: Boolean, discolouration: Boolean) =
        launchSave { repository.saveAccessSiteCheck(patientId, bleeding, swelling, pain, discolouration, loggedByCaregiver) }
    fun submitMedications(takenKeys: List<String>, daptTaken: Boolean) =
        launchSave { repository.saveMedications(patientId, takenKeys, daptTaken, loggedByCaregiver) }
    fun submitActivity(stepsOrMinutes: Int, symptomStopped: String?) =
        launchSave { repository.saveActivity(patientId, stepsOrMinutes, symptomStopped, loggedByCaregiver) }
    fun submitChestPain(count: Int, type: ChestPainType?) =
        launchSave { repository.saveChestPain(patientId, count, type, loggedByCaregiver) }
    fun submitSymptomFlags(palpitations: Boolean, syncope: Boolean, nearSyncope: Boolean) =
        launchSave { repository.saveSymptomFlags(patientId, palpitations, syncope, nearSyncope, loggedByCaregiver) }
    fun submitBreathlessness(nyha: NyhaClass) =
        launchSave { repository.saveBreathlessness(patientId, nyha, loggedByCaregiver) }

    fun finishCheckIn(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                syncManager.syncAll()
            } catch (_: Exception) {
                // Best-effort offline push
            }
            onComplete()
        }
    }

    private fun launchSave(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    /**
     * Stage-4 convenience only: without the Stage 6 staff baseline wizard yet, the demo patient
     * account would otherwise have no baseline and an empty Today screen. Seeds one baseline,
     * once, three days into recovery so most due-field cards have something to show.
     */
    private fun seedDemoBaselineIfNeeded() {
        if (patientId != FakeAuthGateway.DEMO_PATIENT_ID) return
        viewModelScope.launch {
            if (database.patientBaselineDao().get(patientId) != null) return@launch
            val now = System.currentTimeMillis()
            database.patientBaselineDao().upsert(
                PatientBaselineEntity(
                    patientId = patientId,
                    demographics = Demographics(name = "Demo Patient", age = 58, sex = Sex.MALE, contactNumber = "9999999999"),
                    procedural = ProceduralDetails(
                        pciDate = LocalDate.now().minusDays(3),
                        stemiTerritory = StemiTerritory.ANTERIOR,
                        accessSite = AccessSite.RADIAL,
                    ),
                    labsAndVitals = LabsAndVitals(lvefPercent = 45),
                    medicationsAndFollowUp = MedicationsAndFollowUp(
                        daptAgent = "Aspirin + Clopidogrel",
                        betaBlockerDose = "Metoprolol 25mg OD",
                        aceiArbArniDose = "Ramipril 2.5mg OD",
                    ),
                    social = Social(preferredLanguage = PreferredLanguage.ENGLISH),
                    lastCompletedWizardStep = 4,
                    isFinalized = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }
}
