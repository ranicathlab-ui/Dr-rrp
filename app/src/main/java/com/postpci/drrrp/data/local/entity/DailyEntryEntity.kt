package com.postpci.drrrp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.postpci.drrrp.data.model.ChestPainType
import com.postpci.drrrp.data.model.NyhaClass
import com.postpci.drrrp.data.model.SyncStatus
import java.time.LocalDate

/**
 * One day's patient check-in. Client-generates [id] as a UUID (not a server autoincrement) so
 * offline-created entries never collide on sync; sync is last-write-wins per entry [id].
 *
 * All fields are nullable because only the fields due today per [com.postpci.drrrp.data.schedule.MonitoringSchedule]
 * are expected to be filled in on any given day — the Log Entry flow writes one field at a time.
 */
@Entity(
    tableName = "daily_entry",
    indices = [Index(value = ["patientId", "entryDate"])],
)
data class DailyEntryEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val entryDate: LocalDate,
    /** True if a caregiver logged this entry (or a field on it) on the patient's behalf. */
    val loggedByCaregiver: Boolean = false,

    val chestPainCount: Int? = null,
    val chestPainType: ChestPainType? = null,

    val nyhaClass: NyhaClass? = null,

    val restingHeartRate: Int? = null,

    val bpSystolic: Int? = null,
    val bpDiastolic: Int? = null,

    val weightKg: Double? = null,

    val spo2: Int? = null,

    val accessSiteBleeding: Boolean? = null,
    val accessSiteSwelling: Boolean? = null,
    val accessSitePain: Boolean? = null,
    val accessSiteDiscolouration: Boolean? = null,

    /** Comma-separated medication ids the patient marked as taken today. */
    val medicationsTaken: String? = null,
    /** Whether the DAPT drug specifically was taken today — tracked separately per spec. */
    val daptTaken: Boolean? = null,

    val stepsOrMinutesWalked: Int? = null,
    val symptomThatStoppedActivity: String? = null,

    val palpitations: Boolean? = null,
    val syncope: Boolean? = null,
    val nearSyncope: Boolean? = null,

    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

fun DailyEntryEntity.isAnyFieldLogged(): Boolean {
    return restingHeartRate != null ||
        (bpSystolic != null && bpDiastolic != null) ||
        spo2 != null ||
        weightKg != null ||
        (accessSiteBleeding != null || accessSiteSwelling != null || accessSitePain != null || accessSiteDiscolouration != null) ||
        chestPainCount != null ||
        stepsOrMinutesWalked != null ||
        (palpitations != null || syncope != null || nearSyncope != null) ||
        nyhaClass != null ||
        daptTaken != null ||
        !medicationsTaken.isNullOrBlank()
}
