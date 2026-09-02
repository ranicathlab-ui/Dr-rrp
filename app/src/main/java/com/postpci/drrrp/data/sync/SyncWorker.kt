package com.postpci.drrrp.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkerFactory

/** Runs one [SyncManager.syncAll] pass. WorkManager only invokes this when [NetworkType.CONNECTED] holds. */
class SyncWorker(context: Context, params: WorkerParameters, private val syncManager: SyncManager) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = syncManager.syncAll()
        // Any failures are left PENDING and picked up by the next run rather than retried in a
        // tight loop here — WorkManager's own backoff (on the periodic/one-time request) already
        // covers "try again later" without this worker needing its own retry policy.
        return if (result.failed == 0) Result.success() else Result.retry()
    }
}

/**
 * Manual `WorkerFactory` — this project has no DI framework (see [com.postpci.drrrp.DrRrpApplication]
 * doc), so [SyncWorker] can't use WorkManager's default no-arg-constructor instantiation; it needs
 * [SyncManager] injected.
 */
class DrRrpWorkerFactory(private val syncManager: SyncManager) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
        when (workerClassName) {
            SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters, syncManager)
            else -> null
        }
}
