package com.postpci.drrrp.data.sync

import com.postpci.drrrp.data.auth.AuthGateway
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

/**
 * Builds the real, network-backed [SyncApiService]. TLS-only: [BASE_URL] must be `https://`.
 *
 * [BASE_URL] points at the standalone backend in server/index.js, deployed to Render rather than
 * Firebase Cloud Functions — this project's Firebase plan doesn't have Blaze billing enabled, and
 * Cloud Functions requires it (it builds via Cloud Build / stores in Artifact Registry, both
 * billing-gated GCP APIs) even for usage that would stay within the free tier. See
 * server/README.md for the deploy story and functions/index.js's doc comment for the fuller
 * explanation. [BASE_URL] is a placeholder until that Render service exists — update it (and
 * [InviteApiProvider.BASE_URL] to match) once deployed. No PHI goes into logs:
 * [HttpLoggingInterceptor] is BASIC (method/URL/status only) even in debug builds, never BODY.
 */
object RetrofitSyncApiService {
    private const val BASE_URL = "https://dr-rrp-backend.onrender.com/"

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun create(authGateway: AuthGateway): SyncApiService {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(authGateway))
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create()
    }
}
