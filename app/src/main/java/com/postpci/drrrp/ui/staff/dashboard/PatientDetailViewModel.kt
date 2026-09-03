package com.postpci.drrrp.ui.staff.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpci.drrrp.data.local.DrRrpDatabase
import com.postpci.drrrp.data.local.entity.DailyEntryEntity
import com.postpci.drrrp.data.local.entity.PatientBaselineEntity
import com.postpci.drrrp.data.sync.SyncApiService
import com.postpci.drrrp.data.sync.SyncManager
import com.postpci.drrrp.data.sync.dto.CaregiverDto
import com.postpci.drrrp.data.sync.dto.SetCaregiverPermissionRequest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 15

/**
 * Pulls the patient's data down from the server on open (see [SyncManager.pullPatient]) — a
 * baseline created on staff device A and daily entries logged on the patient's own phone only
 * exist in *this* device's local Room after a pull; the local reads below (baseline/entries)
 * don't know or care whether a row got there via a local write or a pull, so nothing else in
 * this ViewModel changes because of it.
 */
class PatientDetailViewModel(
    private val database: DrRrpDatabase,
    private val syncManager: SyncManager,
    private val syncApiService: SyncApiService,
    private val patientId: String,
) : ViewModel() {
    val baseline: StateFlow<PatientBaselineEntity?> =
        database.patientBaselineDao().observe(patientId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var entries by mutableStateOf<List<DailyEntryEntity>>(emptyList())
        private set
    var hasMore by mutableStateOf(true)
        private set
    var isLoadingPage by mutableStateOf(false)
        private set

    // Separate from isLoadingPage (which also gates the "Load more" button/text): stays true
    // until the *first* pull + page fetch has fully finished, so the screen's "No daily entries
    // logged yet." message — gated on entries.isEmpty() — can't flash true during the initial
    // pullPatient() call below, which can take a while (retries, a slow/cold backend) and runs
    // before loadNextPage() ever touches isLoadingPage itself. Reproduced live while the backend
    // was returning 502s, where that gap was long enough to actually show on screen.
    var isInitialLoading by mutableStateOf(true)
        private set

    /** Caregivers linked to this patient, with their current canLogEntries permission — see
     *  [setCaregiverLogging]. Live-fetched (not stored in local Room; caregiver accounts live in
     *  Firestore's users collection, not under this patient), so this list is only as fresh as
     *  the last successful fetch — best-effort, same as everything else network-dependent here. */
    var caregivers by mutableStateOf<List<CaregiverDto>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            // Pull a generous first batch so the existing local-paginated loadNextPage() below
            // has real history to page through, rather than only whatever this device already
            // had cached. Best-effort: a failed pull (offline) just leaves local data as-is.
            try {
                syncManager.pullPatient(patientId, limit = 100)
            } catch (e: Exception) {
                // Offline or the request failed — fall through to whatever's already local.
            }
            fetchNextPage()
            isInitialLoading = false
        }
        refreshCaregivers()
    }

    private fun refreshCaregivers() {
        viewModelScope.launch {
            try {
                caregivers = syncApiService.getCaregivers(patientId)
            } catch (e: Exception) {
                // Best-effort — the "Caregivers" section just stays empty/stale until retried.
            }
        }
    }

    /** Never loads the full history in one call — pages of [PAGE_SIZE], per spec. */
    fun loadNextPage() {
        if (isLoadingPage || !hasMore) return
        viewModelScope.launch { fetchNextPage() }
    }

    private suspend fun fetchNextPage() {
        isLoadingPage = true
        val page = database.dailyEntryDao().getPage(patientId, PAGE_SIZE, entries.size)
        entries = entries + page
        hasMore = page.size == PAGE_SIZE
        isLoadingPage = false
    }

    /** Optimistic: flips the switch immediately so the toggle doesn't feel laggy, then reconciles
     *  with the server — a failure re-fetches the real state rather than leaving a lie on screen. */
    fun setCaregiverLogging(caregiverId: String, canLogEntries: Boolean) {
        caregivers = caregivers.map { if (it.uid == caregiverId) it.copy(canLogEntries = canLogEntries) else it }
        viewModelScope.launch {
            try {
                syncApiService.setCaregiverPermission(caregiverId, SetCaregiverPermissionRequest(canLogEntries))
            } catch (e: Exception) {
                refreshCaregivers()
            }
        }
    }

    fun deletePatient(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                syncManager.deletePatient(patientId)
            } catch (_: Exception) {
                // Best-effort
            }
            onSuccess()
        }
    }
}
