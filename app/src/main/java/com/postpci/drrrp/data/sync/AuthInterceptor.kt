package com.postpci.drrrp.data.sync

import com.postpci.drrrp.data.auth.AuthGateway
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Attaches `Authorization: Bearer <Firebase ID token>` to every request — the backend verifies it. */
class AuthInterceptor(private val authGateway: AuthGateway) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // OkHttp interceptors are synchronous; getIdToken() is a cheap in-memory read for the
        // fake gateway and a (rarely-blocking, Firebase-cached) token fetch for a real one.
        val token = runBlocking { authGateway.getIdToken() }
        val request = chain.request().newBuilder().apply {
            if (token != null) addHeader("Authorization", "Bearer $token")
        }.build()
        return chain.proceed(request)
    }
}
