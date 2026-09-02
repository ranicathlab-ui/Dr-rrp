package com.postpci.drrrp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.postpci.drrrp.data.local.entity.BleedingEventEntity
import com.postpci.drrrp.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BleedingEventDao {
    @Insert
    suspend fun insert(event: BleedingEventEntity)

    @Query("SELECT * FROM bleeding_event WHERE patientId = :patientId ORDER BY timestamp DESC")
    fun observeForPatient(patientId: String): Flow<List<BleedingEventEntity>>

    @Query("SELECT * FROM bleeding_event WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<BleedingEventEntity>

    @Query("UPDATE bleeding_event SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: SyncStatus)
}
