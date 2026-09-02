package com.postpci.drrrp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.postpci.drrrp.data.local.entity.PatientBaselineEntity
import com.postpci.drrrp.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientBaselineDao {
    @Upsert
    suspend fun upsert(baseline: PatientBaselineEntity)

    @Query("SELECT * FROM patient_baseline WHERE patientId = :patientId")
    fun observe(patientId: String): Flow<PatientBaselineEntity?>

    @Query("SELECT * FROM patient_baseline WHERE patientId = :patientId")
    suspend fun get(patientId: String): PatientBaselineEntity?

    @Query("SELECT * FROM patient_baseline ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PatientBaselineEntity>>

    @Query("DELETE FROM patient_baseline WHERE patientId = :patientId")
    suspend fun delete(patientId: String)

    @Query("SELECT * FROM patient_baseline WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<PatientBaselineEntity>

    @Query("UPDATE patient_baseline SET syncStatus = :status WHERE patientId = :patientId")
    suspend fun setSyncStatus(patientId: String, status: SyncStatus)
}
