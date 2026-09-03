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
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The REST contract from the product spec, one endpoint per function. The backend is expected to
 * verify the Firebase ID token [AuthInterceptor] attaches to every call — this interface makes no
 * assumption about how, that's entirely server-side.
 *
 * [RetrofitSyncApiService] implements this against a real backend; [FakeSyncApiService] simulates
 * success locally when no backend URL is configured (see [SyncApiProvider]).
 */
interface SyncApiService {
    @POST("auth/register-device")
    suspend fun registerDevice(@Body body: DeviceRegisterRequest)

    @POST("patient/baseline")
    suspend fun uploadBaseline(@Body body: BaselineDto)

    @PUT("patient/baseline/{patientId}")
    suspend fun editBaseline(@Path("patientId") patientId: String, @Body body: BaselineDto)

    @POST("patient/daily/{patientId}")
    suspend fun postDailyEntry(@Path("patientId") patientId: String, @Body body: DailyEntryDto)

    /** Not in the original spec's endpoint list — added so bleeding events actually reach the
     *  backend instead of only riding along implicitly (see SyncManager's push-drain doc). */
    @POST("patient/bleeding-event/{patientId}")
    suspend fun postBleedingEvent(@Path("patientId") patientId: String, @Body body: BleedingEventDto)

    @GET("patient/{patientId}")
    suspend fun getPatient(
        @Path("patientId") patientId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): PatientDetailResponse

    @GET("staff/patients")
    suspend fun getStaffPatients(@Query("search") search: String? = null, @Query("sort") sort: String? = null): List<PatientListItemDto>

    @DELETE("patient/{patientId}")
    suspend fun deletePatient(@Path("patientId") patientId: String)

    /** Not in the original spec's endpoint list — needed so staff can actually see and manage a
     *  patient's linked caregiver(s) (in particular, toggle canLogEntries), which was previously
     *  enforced everywhere but had no UI path to ever set to false. */
    @GET("patient/{patientId}/caregivers")
    suspend fun getCaregivers(@Path("patientId") patientId: String): List<CaregiverDto>

    @PUT("caregiver/{caregiverId}/permissions")
    suspend fun setCaregiverPermission(@Path("caregiverId") caregiverId: String, @Body body: SetCaregiverPermissionRequest)

    // patientId is part of the path, not just alertId — see server/index.js's doc on this route
    // for why: a cross-patient collectionGroup search by alertId alone needed a Firestore index
    // that was never created and was crashing the backend on every retry.
    @POST("alert/acknowledge/{patientId}/{alertId}")
    suspend fun acknowledgeAlert(@Path("patientId") patientId: String, @Path("alertId") alertId: String, @Body body: AlertAcknowledgeRequest)

    @POST("message/{patientId}")
    suspend fun sendMessage(@Path("patientId") patientId: String, @Body body: MessageDto)

    /** Not in the original spec's endpoint list — needed so a message thread actually reaches a
     *  device other than the one that sent it (see SyncManager.pullMessages). */
    @GET("message/{patientId}")
    suspend fun getMessages(@Path("patientId") patientId: String): List<MessageDto>
}
