package com.postpci.drrrp.ui.staff.wizard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpci.drrrp.data.auth.AuthGateway
import com.postpci.drrrp.data.auth.InviteCredentials
import com.postpci.drrrp.data.local.DrRrpDatabase
import com.postpci.drrrp.data.local.entity.Demographics
import com.postpci.drrrp.data.local.entity.LabsAndVitals
import com.postpci.drrrp.data.local.entity.MedicationsAndFollowUp
import com.postpci.drrrp.data.local.entity.PatientBaselineEntity
import com.postpci.drrrp.data.local.entity.ProceduralDetails
import com.postpci.drrrp.data.local.entity.Social
import com.postpci.drrrp.data.model.SyncStatus
import kotlinx.coroutines.launch

/** The five wizard sections, in the exact order the spec requires. */
val WIZARD_STEP_TITLES = listOf("Demographics", "Procedural", "Labs & Vitals", "Medications & Follow-up", "Social")

/**
 * Backs the staff baseline wizard for both flows: a brand-new patient (no `initialPatientId`,
 * an invite is minted the moment staff leaves step 0) and editing an existing patient's baseline
 * (loads and resumes from `lastCompletedWizardStep + 1`, per the "save progress, exit and
 * resume" requirement).
 */
class BaselineWizardViewModel(
    private val database: DrRrpDatabase,
    private val authGateway: AuthGateway,
    initialPatientId: String?,
    /** Same "sync promptly, not just on the 15-minute cadence" hook as the repositories — see
     *  [com.postpci.drrrp.data.sync.SyncScheduler]. The wizard writes straight to the DAO rather
     *  than through a repository, so it needs its own trigger. */
    private val onSaved: () -> Unit = {},
) : ViewModel() {
    var patientId: String? = initialPatientId
        private set
    var currentStep by mutableStateOf(0)
        private set
    var draft by mutableStateOf(emptyDraft())
        private set
    var inviteCredentials by mutableStateOf<InviteCredentials?>(null)
        private set
    var isLoading by mutableStateOf(initialPatientId != null)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var isComplete by mutableStateOf(false)
        private set

    init {
        initialPatientId?.let { id ->
            viewModelScope.launch {
                database.patientBaselineDao().get(id)?.let { existing ->
                    draft = existing
                    currentStep = (existing.lastCompletedWizardStep + 1).coerceIn(0, 4)
                }
                isLoading = false
            }
        }
    }

    fun updateDemographics(d: Demographics) { draft = draft.copy(demographics = d) }
    fun updateProcedural(p: ProceduralDetails) { draft = draft.copy(procedural = p) }
    fun updateLabs(l: LabsAndVitals) { draft = draft.copy(labsAndVitals = l) }
    fun updateMeds(m: MedicationsAndFollowUp) { draft = draft.copy(medicationsAndFollowUp = m) }
    fun updateSocial(s: Social) { draft = draft.copy(social = s) }

    fun goBack() {
        if (currentStep > 0) currentStep -= 1
    }

    fun saveCurrentStepAndAdvance() {
        viewModelScope.launch {
            isSaving = true
            val id = patientId ?: run {
                val creds = authGateway.createPatientInvite(draft.demographics.name, draft.demographics.contactNumber)
                inviteCredentials = creds
                patientId = creds.patientId
                creds.patientId
            }
            val now = System.currentTimeMillis()
            val finishing = currentStep == 4
            val updated = draft.copy(
                patientId = id,
                lastCompletedWizardStep = maxOf(draft.lastCompletedWizardStep, currentStep),
                isFinalized = draft.isFinalized || finishing,
                createdAt = if (draft.createdAt == 0L) now else draft.createdAt,
                updatedAt = now,
                // Every save is a local edit, so it always needs (re-)pushing even if a
                // previous version of this baseline had already synced.
                syncStatus = SyncStatus.PENDING,
            )
            database.patientBaselineDao().upsert(updated)
            draft = updated
            isSaving = false
            onSaved()
            if (finishing) isComplete = true else currentStep += 1
        }
    }

    companion object {
        private fun emptyDraft() = PatientBaselineEntity(
            patientId = "",
            demographics = Demographics(),
            procedural = ProceduralDetails(),
            labsAndVitals = LabsAndVitals(),
            medicationsAndFollowUp = MedicationsAndFollowUp(),
            social = Social(),
            createdAt = 0L,
            updatedAt = 0L,
        )
    }
}
