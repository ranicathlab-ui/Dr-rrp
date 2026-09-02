package com.postpci.drrrp.ui.staff.caregiver

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpci.drrrp.data.auth.AuthGateway
import com.postpci.drrrp.data.auth.InviteCredentials
import kotlinx.coroutines.launch

/** Staff-only: links a new caregiver account to an existing patient — see [AuthGateway.createCaregiverInvite]. */
class AddCaregiverViewModel(
    private val authGateway: AuthGateway,
    private val patientId: String,
) : ViewModel() {
    var name by mutableStateOf("")
        private set
    var contact by mutableStateOf("")
        private set
    var isSaving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var inviteCredentials by mutableStateOf<InviteCredentials?>(null)
        private set

    fun onNameChange(value: String) {
        name = value
    }

    fun onContactChange(value: String) {
        contact = value
    }

    fun createInvite() {
        if (name.isBlank()) {
            errorMessage = "Enter the caregiver's name."
            return
        }
        viewModelScope.launch {
            isSaving = true
            errorMessage = null
            try {
                inviteCredentials = authGateway.createCaregiverInvite(name.trim(), contact.trim(), patientId)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not create the caregiver invite."
            } finally {
                isSaving = false
            }
        }
    }
}
