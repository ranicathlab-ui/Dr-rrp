package com.postpci.drrrp.data.sync

import com.postpci.drrrp.data.sync.dto.AlertAcknowledgeRequest
import com.postpci.drrrp.data.sync.dto.BaselineDto
import com.postpci.drrrp.data.sync.dto.BleedingEventDto
import com.postpci.drrrp.data.sync.dto.CaregiverDto
import com.postpci.drrrp.data.sync.dto.DailyEntryDto
import com.postpci.drrrp.data.sync.dto.DeviceRegisterRequest
import com.postpci.drrrp.data.sync.dto.MessageDto
import com.postpci.drrrp.data.sync.dto.PatientDetailResponse
import com.postpci.drrrp.data.sync.dto.PatientListItemDto
import com.postpci.drrrp.data.sync.dto.SetCaregiverPermissionRequest
import kotlinx.coroutines.delay

/**
 * In-memory stand-in for the real backend — see [SyncApiService] doc. Simulates a short network
 * delay and always succeeds, so the offline-queue → sync → SYNCED path is demonstrable end to end
 * on-device before any real backend exists. Swap for [RetrofitSyncApiService] in
 * [SyncApiProvider] once one does.
 */
class FakeSyncApiService : SyncApiService {
    override suspend fun registerDevice(body: DeviceRegisterRequest) {
        delay(300)
    }

    override suspend fun uploadBaseline(body: BaselineDto) {
        delay(300)
    }

    override suspend fun editBaseline(patientId: String, body: BaselineDto) {
        delay(300)
    }

    override suspend fun postDailyEntry(patientId: String, body: DailyEntryDto) {
        delay(300)
    }

    override suspend fun postBleedingEvent(patientId: String, body: BleedingEventDto) {
        delay(300)
    }

    override suspend fun getPatient(patientId: String, cursor: String?, limit: Int): PatientDetailResponse {
        delay(300)
        return PatientDetailResponse(baseline = null, dailyEntries = emptyList(), alerts = emptyList(), nextCursor = null)
    }

    override suspend fun getStaffPatients(search: String?, sort: String?): List<PatientListItemDto> {
        delay(300)
        return emptyList()
    }

    override suspend fun acknowledgeAlert(patientId: String, alertId: String, body: AlertAcknowledgeRequest) {
        delay(300)
    }

    override suspend fun sendMessage(patientId: String, body: MessageDto) {
        delay(300)
    }

    override suspend fun getMessages(patientId: String): List<MessageDto> {
        delay(300)
        return emptyList()
    }

    override suspend fun getCaregivers(patientId: String): List<CaregiverDto> {
        delay(300)
        return emptyList()
    }

    override suspend fun setCaregiverPermission(caregiverId: String, body: SetCaregiverPermissionRequest) {
        delay(300)
    }

    override suspend fun deletePatient(patientId: String) {
        delay(300)
    }
}
