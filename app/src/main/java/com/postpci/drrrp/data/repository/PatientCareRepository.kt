package com.postpci.drrrp.data.repository

import com.postpci.drrrp.data.alert.AlertDraft
import com.postpci.drrrp.data.alert.AlertRules
import com.postpci.drrrp.data.local.dao.AlertDao
import com.postpci.drrrp.data.local.dao.BleedingEventDao
import com.postpci.drrrp.data.local.dao.DailyEntryDao
import com.postpci.drrrp.data.local.entity.AlertEntity
import com.postpci.drrrp.data.local.entity.BleedingEventEntity
import com.postpci.drrrp.data.local.entity.DailyEntryEntity
import com.postpci.drrrp.data.model.AlertSourceType
import com.postpci.drrrp.data.model.BleedingSeverity
import com.postpci.drrrp.data.model.ChestPainType
import com.postpci.drrrp.data.model.NyhaClass
import com.postpci.drrrp.data.model.SyncStatus
import com.postpci.drrrp.data.schedule.MonitoringSchedule
import java.time.LocalDate
import java.util.UUID

/**
 * Owns writes to daily entries and bleeding events, and runs every write through
 * [AlertRules] so "any out-of-range reading auto-triggers an alert" (see product spec) happens
 * in exactly one place rather than being re-implemented per screen.
 */
class PatientCareRepository(
    private val dailyEntryDao: DailyEntryDao,
    private val bleedingEventDao: BleedingEventDao,
    private val alertDao: AlertDao,
    /** Called after every local write — wired to [com.postpci.drrrp.data.sync.SyncScheduler.requestImmediateSync]
     *  so a fresh entry gets pushed promptly rather than waiting for the 15-minute periodic sync. */
    private val onLocalWrite: () -> Unit = {},
) {
    private suspend fun updateTodayEntry(
        patientId: String,
        loggedByCaregiver: Boolean,
        mutate: (DailyEntryEntity) -> DailyEntryEntity,
    ): DailyEntryEntity {
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val existing = dailyEntryDao.getForDate(patientId, today)
        val base = existing ?: DailyEntryEntity(
            id = UUID.randomUUID().toString(),
            patientId = patientId,
            entryDate = today,
            createdAt = now,
            updatedAt = now,
        )
        val updated = mutate(base).copy(
            updatedAt = now,
            loggedByCaregiver = base.loggedByCaregiver || loggedByCaregiver,
            // Every field edit is a local change, even to an already-synced entry, so it must
            // go out again — see the equivalent note on the baseline wizard's save function.
            syncStatus = SyncStatus.PENDING,
        )
        dailyEntryDao.upsert(updated)
        onLocalWrite()
        return updated
    }

    private suspend fun raiseAlert(patientId: String, sourceId: String, draft: AlertDraft?, fieldKey: String? = null) {
        if (draft == null) {
            // New Safe Reading Auto-Clear: clear old unreviewed warnings for this field
            fieldKey?.let { key ->
                alertDao.markReviewedForField(patientId, key, System.currentTimeMillis())
            }
            return
        }
        val targetFieldKey = draft.fieldKey
        val alreadyRaised = alertDao.countUnreviewedForSourceAndField(sourceId, targetFieldKey) > 0
        if (alreadyRaised) return
        alertDao.upsert(
            AlertEntity(
                id = "${sourceId}_${targetFieldKey}",
                patientId = patientId,
                sourceType = AlertSourceType.DAILY_ENTRY,
                sourceId = sourceId,
                fieldKey = targetFieldKey,
                severity = draft.severity,
                message = draft.message,
                normalRangeText = draft.normalRangeText,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    fun observeTodayEntry(patientId: String) = dailyEntryDao.observeForDate(patientId, LocalDate.now())

    fun observeUnreviewedAlerts(patientId: String) = alertDao.observeUnreviewedForPatient(patientId)

    fun observeAllAlerts(patientId: String) = alertDao.observeForPatient(patientId)

    suspend fun markAlertReviewed(alertId: String, staffId: String? = null) {
        alertDao.markReviewed(alertId, System.currentTimeMillis(), staffId)
        onLocalWrite()
    }

    suspend fun saveRestingHeartRate(patientId: String, bpm: Int, loggedByCaregiver: Boolean = false) {
        val entry = updateTodayEntry(patientId, loggedByCaregiver) { it.copy(restingHeartRate = bpm) }
        raiseAlert(patientId, entry.id, AlertRules.checkRestingHeartRate(bpm), MonitoringSchedule.RESTING_HEART_RATE)
    }

    suspend fun saveBloodPressure(patientId: String, systolic: Int, diastolic: Int, loggedByCaregiver: Boolean = false) {
        val entry = updateTodayEntry(patientId, loggedByCaregiver) { it.copy(bpSystolic = systolic, bpDiastolic = diastolic) }
        raiseAlert(patientId, entry.id, AlertRules.checkBloodPressure(systolic, diastolic), MonitoringSchedule.BLOOD_PRESSURE)
    }

    suspend fun saveSpo2(patientId: String, percent: Int, loggedByCaregiver: Boolean = false) {
        val entry = updateTodayEntry(patientId, loggedByCaregiver) { it.copy(spo2 = percent) }
        raiseAlert(patientId, entry.id, AlertRules.checkSpo2(percent), MonitoringSchedule.SPO2)
    }

    suspend fun saveWeight(patientId: String, kg: Double, loggedByCaregiver: Boolean = false) {
        val entry = updateTodayEntry(patientId, loggedByCaregiver) { it.copy(weightKg = kg) }
        val windowStart = LocalDate.now().minusDays(3)
        val recent = dailyEntryDao.getPage(patientId, limit = 10, offset = 0)
            .filter { !it.entryDate.isBefore(windowStart) && it.entryDate.isBefore(LocalDate.now()) }
        raiseAlert(patientId, entry.id, AlertRules.checkWeightGain(kg, recent), MonitoringSchedule.WEIGHT)
    }

    suspend fun saveAccessSiteCheck(
        patientId: String,
        bleeding: Boolean,
        swelling: Boolean,
        pain: Boolean,
        discolouration: Boolean,
        loggedByCaregiver: Boolean = false,
    ) {
        val entry = updateTodayEntry(patientId, loggedByCaregiver) {
            it.copy(accessSiteBleeding = bleeding, accessSiteSwelling = swelling, accessSitePain = pain, accessSiteDiscolouration = discolouration)
        }
        raiseAlert(patientId, entry.id, AlertRules.checkAccessSite(bleeding, swelling, pain, discolouration), MonitoringSchedule.ACCESS_SITE_CHECK)
    }

    suspend fun saveMedications(patientId: String, medsTakenKeys: List<String>, daptTaken: Boolean, loggedByCaregiver: Boolean = false) {
        val entry = updateTodayEntry(patientId, loggedByCaregiver) {
            it.copy(medicationsTaken = medsTakenKeys.joinToString(","), daptTaken = daptTaken)
        }
        raiseAlert(patientId, entry.id, AlertRules.checkDaptTaken(daptTaken), MonitoringSchedule.MEDICATIONS_TAKEN)
    }

    suspend fun saveActivity(patientId: String, stepsOrMinutes: Int, symptomThatStoppedActivity: String?, loggedByCaregiver: Boolean = false) {
        updateTodayEntry(patientId, loggedByCaregiver) {
            it.copy(stepsOrMinutesWalked = stepsOrMinutes, symptomThatStoppedActivity = symptomThatStoppedActivity)
        }
    }

    suspend fun saveChestPain(patientId: String, count: Int, type: ChestPainType?, loggedByCaregiver: Boolean = false) {
        val entry = updateTodayEntry(patientId, loggedByCaregiver) { it.copy(chestPainCount = count, chestPainType = type) }
        raiseAlert(patientId, entry.id, AlertRules.checkChestPain(count, type), MonitoringSchedule.CHEST_PAIN)
    }

    suspend fun saveSymptomFlags(patientId: String, palpitations: Boolean, syncope: Boolean, nearSyncope: Boolean, loggedByCaregiver: Boolean = false) {
        val entry = updateTodayEntry(patientId, loggedByCaregiver) {
            it.copy(palpitations = palpitations, syncope = syncope, nearSyncope = nearSyncope)
        }
        raiseAlert(patientId, entry.id, AlertRules.checkSymptomFlags(palpitations, syncope, nearSyncope), MonitoringSchedule.PALPITATIONS_SYNCOPE)
    }

    suspend fun saveBreathlessness(patientId: String, nyha: NyhaClass, loggedByCaregiver: Boolean = false) {
        val entry = updateTodayEntry(patientId, loggedByCaregiver) { it.copy(nyhaClass = nyha) }
        raiseAlert(patientId, entry.id, AlertRules.checkBreathlessness(nyha), MonitoringSchedule.BREATHLESSNESS)
    }

    suspend fun logBleedingEvent(
        patientId: String,
        site: String,
        severity: BleedingSeverity,
        neededMedicalAttention: Boolean,
        notes: String?,
        loggedByCaregiver: Boolean = false,
    ) {
        val event = BleedingEventEntity(
            id = UUID.randomUUID().toString(),
            patientId = patientId,
            timestamp = System.currentTimeMillis(),
            site = site,
            severity = severity,
            neededMedicalAttention = neededMedicalAttention,
            notes = notes,
            loggedByCaregiver = loggedByCaregiver,
        )
        bleedingEventDao.insert(event)
        val draft = AlertRules.checkBleedingEvent(event)
        if (draft != null) {
            alertDao.upsert(
                AlertEntity(
                    // Same deterministic `${sourceId}_${fieldKey}` scheme as raiseAlert() above —
                    // matches functions/index.js's server-side trigger exactly.
                    id = "${event.id}_${draft.fieldKey}",
                    patientId = patientId,
                    sourceType = AlertSourceType.BLEEDING_EVENT,
                    sourceId = event.id,
                    fieldKey = draft.fieldKey,
                    severity = draft.severity,
                    message = draft.message,
                    normalRangeText = draft.normalRangeText,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        onLocalWrite()
    }
}
