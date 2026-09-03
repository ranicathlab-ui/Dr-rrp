package com.postpci.drrrp.ui.home

import com.postpci.drrrp.data.local.dao.AlertDao
import com.postpci.drrrp.data.local.dao.BleedingEventDao
import com.postpci.drrrp.data.local.dao.DailyEntryDao
import com.postpci.drrrp.data.local.entity.AlertEntity
import com.postpci.drrrp.data.local.entity.BleedingEventEntity
import com.postpci.drrrp.data.local.entity.DailyEntryEntity
import com.postpci.drrrp.data.model.AlertSeverity
import com.postpci.drrrp.data.model.AlertSourceType
import com.postpci.drrrp.data.model.SyncStatus
import com.postpci.drrrp.data.repository.PatientCareRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * [EmergencyGateViewModel] is what decides whether the full-screen emergency takeover shows at
 * all — the highest-stakes piece of UI logic in the app (see DR RRP step 8: prioritize the
 * emergency-escalation path over broader UI tests). These are pure-JVM tests against
 * [PatientCareRepository] backed by minimal in-memory fakes of its three DAOs, so no
 * Android/Robolectric/instrumented test infra is needed to cover the actual trigger logic:
 * "which severities show the takeover", "which alert wins when several are pending", and
 * "dismiss vs. a genuinely new alert". "Correct number dialed" is a UI-layer concern
 * (EmergencyAlertScreen's dial Intent) that needs an instrumented Compose test against a real
 * device/emulator — not run here; see ClinicContactTest for the plain-data regression guard on
 * the number itself.
 */
private class FakeAlertDao : AlertDao {
    private val state = MutableStateFlow<List<AlertEntity>>(emptyList())

    fun seed(vararg alerts: AlertEntity) {
        state.value = alerts.toList()
    }

    override suspend fun upsert(alert: AlertEntity) {
        state.value = state.value.filterNot { it.id == alert.id } + alert
    }

    override fun observeForPatient(patientId: String): Flow<List<AlertEntity>> =
        state.map { list -> list.filter { it.patientId == patientId }.sortedByDescending { it.createdAt } }

    override fun observeUnreviewedForPatient(patientId: String): Flow<List<AlertEntity>> =
        state.map { list -> list.filter { it.patientId == patientId && !it.reviewed }.sortedByDescending { it.createdAt } }

    override fun observeMostRecentPerPatient(): Flow<List<AlertEntity>> =
        state.map { list -> list.groupBy { it.patientId }.values.mapNotNull { group -> group.maxByOrNull(AlertEntity::createdAt) } }

    override suspend fun markReviewed(alertId: String, reviewedAt: Long, staffId: String?) {
        state.value = state.value.map {
            if (it.id == alertId) it.copy(reviewed = true, reviewedAt = reviewedAt, reviewedByStaffId = staffId) else it
        }
    }

    override suspend fun getPendingSync(): List<AlertEntity> = state.value.filter { it.syncStatus != SyncStatus.SYNCED }

    override suspend fun countUnreviewedForSourceAndField(sourceId: String, fieldKey: String): Int =
        state.value.count { it.sourceId == sourceId && it.fieldKey == fieldKey && !it.reviewed }

    override suspend fun setSyncStatus(id: String, status: SyncStatus) {
        state.value = state.value.map { if (it.id == id) it.copy(syncStatus = status) else it }
    }

    override suspend fun deleteForPatient(patientId: String) {
        state.value = state.value.filterNot { it.patientId == patientId }
    }
}

/** Unused by these tests beyond satisfying PatientCareRepository's constructor — no-op. */
private class FakeDailyEntryDao : DailyEntryDao {
    override suspend fun upsert(entry: DailyEntryEntity) {}
    override suspend fun getForDate(patientId: String, date: LocalDate): DailyEntryEntity? = null
    override fun observeForDate(patientId: String, date: LocalDate): Flow<DailyEntryEntity?> = MutableStateFlow(null)
    override fun observeAllForPatient(patientId: String): Flow<List<DailyEntryEntity>> = MutableStateFlow(emptyList())
    override suspend fun getPage(patientId: String, limit: Int, offset: Int): List<DailyEntryEntity> = emptyList()
    override suspend fun getLatestForPatient(patientId: String): DailyEntryEntity? = null
    override fun observeRange(patientId: String, from: LocalDate, to: LocalDate): Flow<List<DailyEntryEntity>> = MutableStateFlow(emptyList())
    override suspend fun getPendingSync(): List<DailyEntryEntity> = emptyList()
    override suspend fun setSyncStatus(id: String, status: SyncStatus) {}
    override suspend fun deleteForPatient(patientId: String) {}
}

/** Unused by these tests beyond satisfying PatientCareRepository's constructor — no-op. */
private class FakeBleedingEventDao : BleedingEventDao {
    override suspend fun insert(event: BleedingEventEntity) {}
    override fun observeForPatient(patientId: String): Flow<List<BleedingEventEntity>> = MutableStateFlow(emptyList())
    override suspend fun getPendingSync(): List<BleedingEventEntity> = emptyList()
    override suspend fun setSyncStatus(id: String, status: SyncStatus) {}
    override suspend fun deleteForPatient(patientId: String) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyGateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // EmergencyGateViewModel's stateIn runs on viewModelScope, i.e. Dispatchers.Main.immediate.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun alert(id: String, severity: AlertSeverity, createdAt: Long, patientId: String = "p1") = AlertEntity(
        id = id,
        patientId = patientId,
        sourceType = AlertSourceType.DAILY_ENTRY,
        sourceId = "entry-$id",
        fieldKey = "chestPain",
        severity = severity,
        message = "test",
        createdAt = createdAt,
    )

    /** stateIn(..., SharingStarted.WhileSubscribed, ...) only starts collecting its upstream once
     *  something subscribes — reading .value alone would never trigger that. backgroundScope
     *  (auto-cancelled at the end of runTest) is that subscriber for the life of each test. */
    private fun TestScope.keepAlive(viewModel: EmergencyGateViewModel) {
        viewModel.pendingEmergencyAlert.onEach { }.launchIn(backgroundScope)
    }

    @Test
    fun onlyEmergencySeverityAlerts_triggerTheTakeover() = runTest(dispatcher) {
        val alertDao = FakeAlertDao()
        alertDao.seed(alert("routine-1", AlertSeverity.ROUTINE, 1_000), alert("info-1", AlertSeverity.INFO, 2_000))
        val repo = PatientCareRepository(FakeDailyEntryDao(), FakeBleedingEventDao(), alertDao)
        val viewModel = EmergencyGateViewModel(repo, "p1")
        keepAlive(viewModel)

        advanceUntilIdle()
        assertNull("Routine/info alerts must never trigger the full-screen emergency takeover", viewModel.pendingEmergencyAlert.value)
    }

    @Test
    fun mostRecentEmergencyAlert_isTheOneShown() = runTest(dispatcher) {
        val alertDao = FakeAlertDao()
        alertDao.seed(alert("em-old", AlertSeverity.EMERGENCY, 1_000), alert("em-new", AlertSeverity.EMERGENCY, 5_000))
        val repo = PatientCareRepository(FakeDailyEntryDao(), FakeBleedingEventDao(), alertDao)
        val viewModel = EmergencyGateViewModel(repo, "p1")
        keepAlive(viewModel)

        advanceUntilIdle()
        assertEquals("em-new", viewModel.pendingEmergencyAlert.value?.id)
    }

    @Test
    fun anotherPatientsEmergencyAlert_neverShows() = runTest(dispatcher) {
        val alertDao = FakeAlertDao()
        alertDao.seed(alert("other-patient-em", AlertSeverity.EMERGENCY, 9_999, patientId = "someone-else"))
        val repo = PatientCareRepository(FakeDailyEntryDao(), FakeBleedingEventDao(), alertDao)
        val viewModel = EmergencyGateViewModel(repo, "p1")
        keepAlive(viewModel)

        advanceUntilIdle()
        assertNull("An emergency alert for a different patient must never interrupt this screen", viewModel.pendingEmergencyAlert.value)
    }

    @Test
    fun dismissing_clearsTheTakeover_butANewEmergencyAlertReinterrupts() = runTest(dispatcher) {
        val alertDao = FakeAlertDao()
        alertDao.seed(alert("em-1", AlertSeverity.EMERGENCY, 1_000))
        val repo = PatientCareRepository(FakeDailyEntryDao(), FakeBleedingEventDao(), alertDao)
        val viewModel = EmergencyGateViewModel(repo, "p1")
        keepAlive(viewModel)

        advanceUntilIdle()
        assertNotNull(viewModel.pendingEmergencyAlert.value)

        viewModel.dismiss("em-1")
        advanceUntilIdle()
        assertNull(
            "Dismissing must clear the takeover and mark the alert reviewed so it won't re-appear on future app launches",
            viewModel.pendingEmergencyAlert.value,
        )

        alertDao.seed(alert("em-1", AlertSeverity.EMERGENCY, 1_000), alert("em-2", AlertSeverity.EMERGENCY, 2_000))
        advanceUntilIdle()
        assertEquals(
            "A newer, different emergency alert must still interrupt after an earlier one was dismissed",
            "em-2",
            viewModel.pendingEmergencyAlert.value?.id,
        )
    }
}
