package com.postpci.drrrp.data.sync

import com.postpci.drrrp.data.auth.AuthGateway

/**
 * Single switch point for going live. `true`: the real Cloud Functions backend
 * ([RetrofitSyncApiService], functions/index.js). `false`: [FakeSyncApiService] — flip back to
 * this if the backend is ever unreachable and offline-only demo behavior is wanted again;
 * nothing else in the sync layer needs to change either way.
 */
object SyncApiProvider {
    private const val USE_REAL_BACKEND = true

    fun create(authGateway: AuthGateway): SyncApiService =
        if (USE_REAL_BACKEND) RetrofitSyncApiService.create(authGateway) else FakeSyncApiService()
}
