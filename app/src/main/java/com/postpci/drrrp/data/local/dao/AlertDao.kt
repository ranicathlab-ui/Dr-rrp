package com.postpci.drrrp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.postpci.drrrp.data.local.entity.AlertEntity
import com.postpci.drrrp.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    // Upsert, not insert: a pulled-down server alert can legitimately share an id with a
    // locally-raised one (both use the same deterministic `${sourceId}_${fieldKey}` scheme —
    // see PatientCareRepository.raiseAlert), and that's meant to converge onto one row, not
    // conflict.
    @Upsert
    suspend fun upsert(alert: AlertEntity)

    @Query("SELECT * FROM alert WHERE patientId = :patientId ORDER BY createdAt DESC")
    fun observeForPatient(patientId: String): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alert WHERE patientId = :patientId AND reviewed = 0 ORDER BY createdAt DESC")
    fun observeUnreviewedForPatient(patientId: String): Flow<List<AlertEntity>>

    /** Most recent alert per patient, newest first — backs the "sorted by most recent flag" dashboard list. */
    @Query(
        """
        SELECT a.* FROM alert a
        INNER JOIN (
            SELECT patientId, MAX(createdAt) AS maxCreatedAt FROM alert GROUP BY patientId
        ) latest ON a.patientId = latest.patientId AND a.createdAt = latest.maxCreatedAt
        ORDER BY a.createdAt DESC
        """,
    )
    fun observeMostRecentPerPatient(): Flow<List<AlertEntity>>

    // Resets syncStatus to PENDING so the review gets pushed even if the alert had already synced.
    @Query("UPDATE alert SET reviewed = 1, reviewedAt = :reviewedAt, reviewedByStaffId = :staffId, syncStatus = 'PENDING' WHERE id = :alertId")
    suspend fun markReviewed(alertId: String, reviewedAt: Long, staffId: String?)

    @Query("UPDATE alert SET reviewed = 1, reviewedAt = :reviewedAt, syncStatus = 'PENDING' WHERE patientId = :patientId AND fieldKey = :fieldKey AND reviewed = 0")
    suspend fun markReviewedForField(patientId: String, fieldKey: String, reviewedAt: Long)

    @Query("SELECT * FROM alert WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<AlertEntity>

    /** Dedupe check: has this exact source+field already raised an unreviewed alert? */
    @Query("SELECT COUNT(*) FROM alert WHERE sourceId = :sourceId AND fieldKey = :fieldKey AND reviewed = 0")
    suspend fun countUnreviewedForSourceAndField(sourceId: String, fieldKey: String): Int

    @Query("UPDATE alert SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: SyncStatus)

    @Query("DELETE FROM alert WHERE patientId = :patientId")
    suspend fun deleteForPatient(patientId: String)
}
