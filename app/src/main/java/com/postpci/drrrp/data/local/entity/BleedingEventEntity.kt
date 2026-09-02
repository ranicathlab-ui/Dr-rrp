package com.postpci.drrrp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.postpci.drrrp.data.model.BleedingSeverity
import com.postpci.drrrp.data.model.SyncStatus

/** Event-based (logged as it occurs, not tied to a daily cadence). Client-generated UUID [id]. */
@Entity(
    tableName = "bleeding_event",
    indices = [Index(value = ["patientId"])],
)
data class BleedingEventEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val timestamp: Long,
    val site: String,
    val severity: BleedingSeverity,
    /** Drives the emergency-escalation screen when true. */
    val neededMedicalAttention: Boolean,
    val notes: String? = null,
    val loggedByCaregiver: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
