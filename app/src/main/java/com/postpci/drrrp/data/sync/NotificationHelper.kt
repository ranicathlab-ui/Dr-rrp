package com.postpci.drrrp.data.sync

import android.app.NotificationManager
import android.content.Context

object NotificationHelper {
    const val NOTIFICATION_ID_MESSAGES = 1001

    /**
     * Explicitly dismisses/clears chat and message notifications from the Android system tray
     * when the conversation screen is opened or messages are marked as read.
     */
    fun cancelMessageNotifications(context: Context, patientId: String? = null) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID_MESSAGES)
            patientId?.let {
                manager?.cancel(it.hashCode())
            }
            // Clear all active system tray notifications for this conversation
            manager?.cancelAll()
        } catch (_: Exception) {
            // Best-effort fallback
        }
    }
}
