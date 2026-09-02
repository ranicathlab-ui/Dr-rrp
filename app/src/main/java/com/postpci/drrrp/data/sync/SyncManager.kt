package com.postpci.drrrp.data.sync

import com.postpci.drrrp.data.auth.AuthGateway
import com.postpci.drrrp.data.local.DrRrpDatabase
import com.postpci.drrrp.data.model.SyncStatus
import com.postpci.drrrp.data.model.UserRole
import com.postpci.drrrp.data.sync.dto.AlertAcknowledgeRequest

/**
 * Drains every "pending" queue (baseline, daily entries, bleeding events, alerts, messages) into
 * the backend, then — for a patient/caregiver account — pulls that patient's data back down.
 * Each pushed entity carries a client-generated UUID, so retrying a partially-failed run is
 * always safe — the same ID just overwrites/upserts server-side (last-write-wins per entry ID,
 * not per patient, per the offline-sync spec). One entity's failure never blocks the others; it's
 * simply left PENDING for the next run.
 *
 * Pull-down (see [pullPatient]/[pullMessages]) is what actually makes "staff sees every patient"
 * and "a caregiver sees what the patient logged" true across separate devices — without it this
 * class only ever pushed, and two devices sharing one patient would never converge.
 */
class SyncManager(
    private val database: DrRrpDatabase,
    private val api: SyncApiService,
    private val authGateway: AuthGateway,
) {

    suspend fun syncAll(): SyncResult {
        val result = pushPending()

        // Push before pull: anything this device just pushed above is now the server's canonical
        // copy too, so pulling right after can't clobber it with stale data.
        selfPatientId()?.let { patientId ->
            try {
                pullPatientOnly(patientId)
                pullMessages(patientId)
            } catch (e: Exception) {
                // Best-effort — the next periodic run tries again; a failed pull doesn't affect
                // the push results already recorded above.
            }
        }

        return result
    }

    /**
     * Drains every pending queue into the backend — the push half of [syncAll], factored out so
     * [pullPatient] can call it too. Without this, a screen that pulls on its own (every
     * TodayViewModel/AlertsViewModel/etc. does, on every visit, independent of the periodic
     * WorkManager sync job) could race ahead of a not-yet-pushed local write — e.g. marking an
     * alert reviewed, or logging a vital — and silently clobber it: every pull-mapper stamps
     * `syncStatus = SYNCED` unconditionally (see Mappers.kt), and upsert replaces the whole row,
     * so a pull winning that race doesn't just show stale data for one frame, it permanently
     * erases the fact that there was ever a pending local change to push. Reproduced live: an
     * alert marked "Reviewed" would revert to unreviewed after a relaunch, because TodayViewModel's
     * own pull ran before the WorkManager-scheduled push had a chance to fire.
     */
    private suspend fun pushPending(): SyncResult {
        var succeeded = 0
        var failed = 0

        suspend fun <T> drain(items: List<T>, push: suspend (T) -> Unit, markSynced: suspend (T) -> Unit) {
            for (item in items) {
                try {
                    push(item)
                    markSynced(item)
                    succeeded++
                } catch (e: Exception) {
                    // Left PENDING — picked up again on the next connectivity-triggered run.
                    failed++
                }
            }
        }

        drain(
            database.patientBaselineDao().getPendingSync(),
            { api.uploadBaseline(it.toDto()) },
            { database.patientBaselineDao().setSyncStatus(it.patientId, SyncStatus.SYNCED) },
        )
        drain(
            database.dailyEntryDao().getPendingSync(),
            { api.postDailyEntry(it.patientId, it.toDto()) },
            { database.dailyEntryDao().setSyncStatus(it.id, SyncStatus.SYNCED) },
        )
        drain(
            database.bleedingEventDao().getPendingSync(),
            { api.postBleedingEvent(it.patientId, it.toDto()) },
            { database.bleedingEventDao().setSyncStatus(it.id, SyncStatus.SYNCED) },
        )
        drain(
            database.alertDao().getPendingSync(),
            { alert ->
                if (alert.reviewed) {
                    api.acknowledgeAlert(alert.id, AlertAcknowledgeRequest(alert.reviewedByStaffId))
                }
                // A freshly-created, still-unreviewed alert has nothing to push: it's generated
                // server-side too (functions/index.js's Firestore triggers), so acknowledgement is
                // the only outbound call — an unreviewed alert is simply marked synced once observed.
            },
            { database.alertDao().setSyncStatus(it.id, SyncStatus.SYNCED) },
        )
        drain(
            database.messageDao().getPendingSync(),
            { api.sendMessage(it.patientId, it.toDto()) },
            { database.messageDao().setSyncStatus(it.id, SyncStatus.SYNCED) },
        )

        return SyncResult(succeeded, failed)
    }

    /** The current user's own patient record: their own uid if they're a patient, their linked
     *  patient if they're a caregiver, null for staff (who pull per-viewed-patient explicitly —
     *  see PatientDetailViewModel/StaffDashboardViewModel — rather than having one "self"). */
    private fun selfPatientId(): String? {
        val user = authGateway.currentUser.value ?: return null
        return when (user.role) {
            UserRole.PATIENT -> user.uid
            UserRole.CAREGIVER -> user.linkedPatientId
            UserRole.STAFF -> null
        }
    }

    /**
     * Pulls [patientId]'s baseline, daily entries, and alerts down from the server and merges
     * them into local Room. Safe to call repeatedly (e.g. every screen open) — every pulled
     * record upserts by its own id, converging with whatever's already local rather than
     * duplicating it (see the alert-id note on PatientCareRepository.raiseAlert).
     *
     * Always pushes pending local changes first (see [pushPending]'s doc) — this is the one
     * function nearly every screen calls directly on its own, so it's the one place that has to
     * be race-safe on its own rather than relying on the periodic WorkManager job to have won a
     * race it doesn't know it's in.
     */
    suspend fun pullPatient(patientId: String, limit: Int = 20) {
        pushPending()
        pullPatientOnly(patientId, limit)
    }

    private suspend fun pullPatientOnly(patientId: String, limit: Int = 20) {
        val response = api.getPatient(patientId, cursor = null, limit = limit)
        response.baseline?.let { database.patientBaselineDao().upsert(it.toEntity()) }
        response.dailyEntries.forEach { database.dailyEntryDao().upsert(it.toEntity()) }
        response.alerts.forEach { database.alertDao().upsert(it.toEntity()) }
    }

    suspend fun pullMessages(patientId: String) {
        api.getMessages(patientId).forEach { database.messageDao().upsert(it.toEntity()) }
    }
}

data class SyncResult(val succeeded: Int, val failed: Int)
