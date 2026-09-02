package com.postpci.drrrp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.postpci.drrrp.data.model.SyncStatus
import com.postpci.drrrp.data.model.UserRole

/** One message in a patient's single thread with the clinic — backs the Messaging screens. */
@Entity(
    tableName = "message",
    indices = [Index(value = ["patientId", "timestamp"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val senderRole: UserRole,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val readByStaff: Boolean = false,
    val readByPatient: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
