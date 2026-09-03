package com.postpci.drrrp.data.repository

import com.postpci.drrrp.data.local.dao.MessageDao
import com.postpci.drrrp.data.local.entity.MessageEntity
import com.postpci.drrrp.data.model.UserRole
import java.util.UUID

class MessagingRepository(private val messageDao: MessageDao, private val onLocalWrite: () -> Unit = {}) {
    fun observeThread(patientId: String) = messageDao.observeForPatient(patientId)

    fun observePatientIdsWithUnreadForStaff() = messageDao.observePatientIdsWithUnreadForStaff()

    fun observeUnreadCountForPatient(patientId: String) = messageDao.observeUnreadCountForPatient(patientId)

    fun observeTotalUnreadCountForStaff() = messageDao.observeTotalUnreadCountForStaff()

    suspend fun send(patientId: String, senderRole: UserRole, senderId: String, senderName: String, text: String) {
        val now = System.currentTimeMillis()
        messageDao.upsert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                patientId = patientId,
                senderRole = senderRole,
                senderId = senderId,
                senderName = senderName,
                text = text,
                timestamp = now,
                // A message is "read" by whichever side sent it, unread by the other side.
                readByStaff = senderRole == UserRole.STAFF,
                readByPatient = senderRole != UserRole.STAFF,
            ),
        )
        onLocalWrite()
    }

    suspend fun markReadByStaff(patientId: String) = messageDao.markReadByStaff(patientId)
    suspend fun markReadByPatient(patientId: String) = messageDao.markReadByPatient(patientId)
}
