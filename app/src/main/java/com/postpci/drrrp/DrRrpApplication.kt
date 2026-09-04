package com.postpci.drrrp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import com.google.firebase.messaging.FirebaseMessaging
import com.postpci.drrrp.data.auth.AuthGateway
import com.postpci.drrrp.data.auth.FirebaseAuthGateway
import com.postpci.drrrp.data.local.DrRrpDatabase
import com.postpci.drrrp.data.onboarding.DisclaimerPreferences
import com.postpci.drrrp.data.repository.MessagingRepository
import com.postpci.drrrp.data.repository.PatientCareRepository
import com.postpci.drrrp.data.sync.DrRrpMessagingService
import com.postpci.drrrp.data.sync.DrRrpWorkerFactory
import com.postpci.drrrp.data.sync.SyncApiProvider
import com.postpci.drrrp.data.sync.SyncApiService
import com.postpci.drrrp.data.sync.SyncManager
import com.postpci.drrrp.data.sync.SyncScheduler
import com.postpci.drrrp.data.sync.dto.DeviceRegisterRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Simple manual DI container (no DI framework yet) — exposes the singleton encrypted database
 * and the auth gateway. [authGateway] is the one binding to change when real Firebase Auth is
 * wired in (see [AuthGateway] doc) — everything else reads through the interface.
 *
 * Implements [Configuration.Provider] so WorkManager's on-demand initialization can hand
 * [SyncWorker][com.postpci.drrrp.data.sync.SyncWorker] its [SyncManager] dependency through
 * [DrRrpWorkerFactory] — this app has no DI framework to do that for it.
 */
class DrRrpApplication : Application(), Configuration.Provider {
    val database: DrRrpDatabase by lazy { DrRrpDatabase.build(this) }
    val authGateway: AuthGateway by lazy { FirebaseAuthGateway() }
    val disclaimerPreferences: DisclaimerPreferences by lazy { DisclaimerPreferences(this) }
    private fun onLocalWrite() = SyncScheduler.requestImmediateSync(this)

    // Process-lifetime scope for FCM token registration — this is a background maintenance task
    // with no UI/ViewModel it naturally belongs to (it needs to run for as long as the app
    // process does, tied to authGateway.currentUser rather than any one screen).
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val patientCareRepository: PatientCareRepository by lazy {
        PatientCareRepository(database.dailyEntryDao(), database.bleedingEventDao(), database.alertDao(), ::onLocalWrite)
    }
    val messagingRepository: MessagingRepository by lazy { MessagingRepository(database.messageDao(), ::onLocalWrite) }

    val syncApiService: SyncApiService by lazy { SyncApiProvider.create(authGateway) }
    val syncManager: SyncManager by lazy { SyncManager(database, syncApiService, authGateway) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(DrRrpWorkerFactory(syncManager, database))
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // The actual "sync when back online" mechanism — see SyncScheduler doc. Both calls are
        // constrained to NetworkType.CONNECTED, so they're harmless (and inert) when offline or
        // when there's nothing queued yet.
        SyncScheduler.scheduleRecurring(this)
        SyncScheduler.scheduleStaffReminders(this)
        SyncScheduler.requestImmediateSync(this)
        observeAuthForPushRegistration()
    }

    private fun createNotificationChannels() {
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                DrRrpMessagingService.CHANNEL_EMERGENCY,
                getString(R.string.notification_channel_emergency),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = getString(R.string.notification_channel_emergency_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                DrRrpMessagingService.CHANNEL_ROUTINE,
                getString(R.string.notification_channel_routine),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = getString(R.string.notification_channel_routine_description) },
        )
    }

    /** (Re-)registers the FCM token with the backend whenever a user is signed in — covers both
     * a fresh sign-in and the app already being open when [DrRrpMessagingService.onNewToken]
     * fires for a different reason (token rotation). */
    private fun observeAuthForPushRegistration() {
        applicationScope.launch {
            authGateway.currentUser.collect { user ->
                if (user == null) return@collect
                ensurePushTokenRegistered()
            }
        }
    }

    /** Called by [DrRrpMessagingService.onNewToken] when FCM rotates the device's token. */
    fun onFcmTokenRefreshed(token: String) {
        applicationScope.launch {
            if (authGateway.currentUser.value != null) {
                registerPushToken(token)
            }
        }
    }

    private fun ensurePushTokenRegistered() {
        applicationScope.launch {
            var attempts = 0
            while (attempts < 5 && authGateway.currentUser.value != null) {
                try {
                    val token: String? = FirebaseMessaging.getInstance().token.await()
                    if (!token.isNullOrBlank()) {
                        syncApiService.registerDevice(DeviceRegisterRequest(token))
                        break
                    }
                } catch (_: Exception) {
                    // Retry after delay
                }
                attempts++
                kotlinx.coroutines.delay(2000L * attempts)
            }
        }
    }

    private suspend fun registerPushToken(token: String) {
        try {
            syncApiService.registerDevice(DeviceRegisterRequest(token))
        } catch (_: Exception) {
            // Best-effort
        }
    }
}
