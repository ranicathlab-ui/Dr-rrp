package com.postpci.drrrp.data.sync

import retrofit2.http.Body
import retrofit2.http.POST

/** Wire request/response shapes for the two invite endpoints below. */
data class CreatePatientInviteRequest(val name: String)
data class CreateCaregiverInviteRequest(val name: String, val patientId: String)
data class InviteResponse(val patientId: String, val email: String, val temporaryPassword: String)

/**
 * Staff-only account creation — `POST /invite/patient` and `POST /invite/caregiver` on the same
 * backend as [SyncApiService] (see server/index.js). This used to be two callable Cloud
 * Functions reached via the Firebase Functions SDK; it's plain REST now, for the same reason
 * [SyncApiProvider] points at [RetrofitSyncApiService] instead of a Cloud Function — see that
 * file's doc and server/index.js's for the full story (no Blaze billing plan available).
 */
interface InviteApiService {
    @POST("invite/patient")
    suspend fun createPatientInvite(@Body body: CreatePatientInviteRequest): InviteResponse

    @POST("invite/caregiver")
    suspend fun createCaregiverInvite(@Body body: CreateCaregiverInviteRequest): InviteResponse
}
