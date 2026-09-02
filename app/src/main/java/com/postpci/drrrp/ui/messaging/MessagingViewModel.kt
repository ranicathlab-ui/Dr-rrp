package com.postpci.drrrp.ui.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpci.drrrp.data.local.entity.MessageEntity
import com.postpci.drrrp.data.model.UserRole
import com.postpci.drrrp.data.repository.MessagingRepository
import com.postpci.drrrp.data.sync.SyncManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MessagingViewModel(
    private val repository: MessagingRepository,
    private val syncManager: SyncManager,
    private val patientId: String,
    private val currentUserRole: UserRole,
    private val currentUserId: String,
    private val currentUserName: String,
) : ViewModel() {
    val messages: StateFlow<List<MessageEntity>> =
        repository.observeThread(patientId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            // Pulls the other side's messages down — without this, a patient's reply logged on
            // their own device would never reach the staff device's thread view (or vice versa).
            try {
                syncManager.pullMessages(patientId)
            } catch (e: Exception) {
                // Offline or the request failed — the thread just shows whatever's already local.
            }
            if (currentUserRole == UserRole.STAFF) repository.markReadByStaff(patientId) else repository.markReadByPatient(patientId)
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.send(patientId, currentUserRole, currentUserId, currentUserName, text.trim())
        }
    }
}
