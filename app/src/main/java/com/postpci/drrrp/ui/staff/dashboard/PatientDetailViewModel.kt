package com.postpci.drrrp.ui.staff.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpci.drrrp.data.local.DrRrpDatabase
import com.postpci.drrrp.data.local.entity.DailyEntryEntity
import com.postpci.drrrp.data.local.entity.PatientBaselineEntity
import com.postpci.drrrp.data.sync.SyncManager
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
}
