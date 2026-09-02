package com.postpci.drrrp.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules [SyncWorker] to run whenever the device is connected — this *is* the "syncs when
 * back online" mechanism: [NetworkType.CONNECTED] means WorkManager itself waits for connectivity
 * rather than the app polling for it.
 */
object SyncScheduler {
    private const val PERIODIC_WORK_NAME = "drrrp-periodic-sync"
    private const val ONE_TIME_WORK_NAME = "drrrp-sync-now"

    private val connectedConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Ongoing background safety net — 15 minutes is WorkManager's minimum periodic interval. */
    fun scheduleRecurring(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(connectedConstraint)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Prompt, one-off attempt — runs as soon as [NetworkType.CONNECTED] is satisfied, not on the 15-minute cadence. */
    fun requestImmediateSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(connectedConstraint)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
