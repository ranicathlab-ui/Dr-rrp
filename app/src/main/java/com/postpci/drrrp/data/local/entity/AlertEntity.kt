package com.postpci.drrrp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.postpci.drrrp.data.model.AlertSeverity
import com.postpci.drrrp.data.model.AlertSourceType
import com.postpci.drrrp.data.model.SyncStatus

/**
 * A flagged out-of-range reading, event, or (for [AlertSeverity.INFO]) a missed-entry notice.
 * Generated client-side from range checks (Stage 4) and pushed via FCM (Stage 8).
 */
@Entity(
    tableName = "alert",
    indices = [Index(value = ["patientId", "createdAt"])],
)
data class AlertEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val sourceType: AlertSourceType,
    /** Id of the [DailyEntryEntity] or [BleedingEventEntity] that triggered this, if any. */
    val sourceId: String? = null,
    /** Matches a DailyEntryEntity field name, or a fixed key like "missed:<fieldKey>". */
    val fieldKey: String,
    val severity: AlertSeverity,
    val message: String,
    val normalRangeText: String? = null,
    val createdAt: Long,
    val reviewed: Boolean = false,
    val reviewedAt: Long? = null,
    val reviewedByStaffId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
