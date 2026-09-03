package com.postpci.drrrp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.postpci.drrrp.data.local.entity.MessageEntity
import com.postpci.drrrp.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    // Upsert so pulling the thread down from the server can't conflict with a message this same
    // device already sent (client-generated id, upsert-by-id — same convention as every other
    // synced entity).
    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("SELECT * FROM message WHERE patientId = :patientId ORDER BY timestamp ASC")
    fun observeForPatient(patientId: String): Flow<List<MessageEntity>>

    /** One row per patient with an unread-by-staff message — drives the dashboard's inbox badge. */
    @Query("SELECT DISTINCT patientId FROM message WHERE readByStaff = 0 AND senderRole != 'STAFF'")
    fun observePatientIdsWithUnreadForStaff(): Flow<List<String>>

    @Query("SELECT DISTINCT patientId FROM message")
    fun observePatientIdsWithMessages(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM message WHERE patientId = :patientId AND readByPatient = 0 AND senderRole = 'STAFF'")
    fun observeUnreadCountForPatient(patientId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM message WHERE readByStaff = 0 AND senderRole != 'STAFF'")
    fun observeTotalUnreadCountForStaff(): Flow<Int>

    @Query("UPDATE message SET readByStaff = 1 WHERE patientId = :patientId AND readByStaff = 0")
    suspend fun markReadByStaff(patientId: String)

    @Query("UPDATE message SET readByPatient = 1 WHERE patientId = :patientId AND readByPatient = 0")
    suspend fun markReadByPatient(patientId: String)

    @Query("SELECT * FROM message WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<MessageEntity>

    @Query("UPDATE message SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: SyncStatus)

    @Query("DELETE FROM message WHERE patientId = :patientId")
    suspend fun deleteForPatient(patientId: String)
}
