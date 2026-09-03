package com.postpci.drrrp.data.sync

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.postpci.drrrp.DrRrpApplication
import com.postpci.drrrp.MainActivity
import com.postpci.drrrp.R

/**
 * Receives FCM pushes — the delivery side of "[generated client-side from range checks], and
 * pushed via FCM" (see [com.postpci.drrrp.data.local.entity.AlertEntity]'s doc) — plus device
 * token refreshes.
 *
 * No real backend exists yet ([SyncApiProvider.USE_REAL_BACKEND] is still `false`), so the
 * server-side push payload isn't defined either. This reads a simple placeholder data-message
 * contract until a real one is settled on:
 *  - `title`, `body` — shown as-is (falls back to the FCM "notification" payload fields first,
 *    if the backend ends up sending those instead of a data-only message)
 *  - `severity` — an [com.postpci.drrrp.data.model.AlertSeverity] name; `"EMERGENCY"` posts to
 *    the high-importance channel, everything else to the routine one
 *
 * Notification channels are created once in [DrRrpApplication.onCreate].
 */
class DrRrpMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        (applicationContext as DrRrpApplication).onFcmTokenRefreshed(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: return
        val isEmergency = message.data["severity"] == "EMERGENCY"
        val channelId = if (isEmergency) CHANNEL_EMERGENCY else CHANNEL_ROUTINE

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Notification permission (Android 13+) is requested from MainActivity; if it was denied,
        // notify() below is a documented no-op rather than a crash — nothing else to guard here.
        ContextCompat.getSystemService(this, NotificationManager::class.java)
            ?.notify(message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val CHANNEL_EMERGENCY = "alerts_emergency"
        const val CHANNEL_ROUTINE = "alerts_routine"
    }
}
