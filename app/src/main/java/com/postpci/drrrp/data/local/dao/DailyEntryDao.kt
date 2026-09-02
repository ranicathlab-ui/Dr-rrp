package com.postpci.drrrp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.postpci.drrrp.data.local.entity.DailyEntryEntity
import com.postpci.drrrp.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DailyEntryDao {
    @Upsert
    suspend fun upsert(entry: DailyEntryEntity)

    @Query("SELECT * FROM daily_entry WHERE patientId = :patientId AND entryDate = :date LIMIT 1")
    suspend fun getForDate(patientId: String, date: LocalDate): DailyEntryEntity?

    @Query("SELECT * FROM daily_entry WHERE patientId = :patientId AND entryDate = :date LIMIT 1")
    fun observeForDate(patientId: String, date: LocalDate): Flow<DailyEntryEntity?>

    @Query("SELECT * FROM daily_entry WHERE patientId = :patientId ORDER BY entryDate DESC")
    fun observeAllForPatient(patientId: String): Flow<List<DailyEntryEntity>>

    /** Paginated history for the staff patient-detail screen — never load the full history at once. */
    @Query("SELECT * FROM daily_entry WHERE patientId = :patientId ORDER BY entryDate DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(patientId: String, limit: Int, offset: Int): List<DailyEntryEntity>

    @Query("SELECT * FROM daily_entry WHERE patientId = :patientId ORDER BY entryDate DESC LIMIT 1")
    suspend fun getLatestForPatient(patientId: String): DailyEntryEntity?

    @Query(
        "SELECT * FROM daily_entry WHERE patientId = :patientId AND entryDate BETWEEN :from AND :to ORDER BY entryDate ASC",
    )
    fun observeRange(patientId: String, from: LocalDate, to: LocalDate): Flow<List<DailyEntryEntity>>

    @Query("SELECT * FROM daily_entry WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<DailyEntryEntity>

    @Query("UPDATE daily_entry SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: SyncStatus)
}
