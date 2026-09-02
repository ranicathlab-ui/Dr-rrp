package com.postpci.drrrp.data.sync

import com.postpci.drrrp.data.auth.AuthGateway
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import retrofit2.create
import java.util.concurrent.TimeUnit

/**
 * Builds [InviteApiService] against the same backend as [RetrofitSyncApiService] — same base URL,
 * same [AuthInterceptor] pattern (staff calling these needs a verified ID token exactly like
 * every sync endpoint). Kept as its own small Retrofit client rather than added to
 * [SyncApiService] because [FirebaseAuthGateway][com.postpci.drrrp.data.auth.FirebaseAuthGateway]
 * — which calls this — can't depend on [SyncApiProvider]'s output without a circular dependency
 * ([SyncApiProvider] itself needs an [AuthGateway] for its own [AuthInterceptor]).
 */
object InviteApiProvider {
    // Same backend as RetrofitSyncApiService — update both together (see that file's doc for why
    // this points at Render rather than a Cloud Function).
    private const val BASE_URL = "https://dr-rrp-backend.onrender.com/"

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun create(authGateway: AuthGateway): InviteApiService {
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
