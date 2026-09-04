package com.postpci.drrrp.data.sync

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.postpci.drrrp.MainActivity
import com.postpci.drrrp.R
import com.postpci.drrrp.data.local.DrRrpDatabase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class StaffReminderWorker(
    private val context: Context,
    params: WorkerParameters,
    private val database: DrRrpDatabase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val baselines = database.patientBaselineDao().observeAll().first()

            val today = LocalDate.now()
            val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val patientsWithFollowUpToday = baselines.filter { patient ->
                patient.medicationsAndFollowUp.nextFollowUpDate != null &&
                patient.medicationsAndFollowUp.nextFollowUpDate >= startOfDay &&
                patient.medicationsAndFollowUp.nextFollowUpDate < endOfDay &&
                patient.medicationsAndFollowUp.followUpStatus == null
            }

            val patientsWithEchoToday = baselines.filter { patient ->
                patient.medicationsAndFollowUp.nextEchoDate != null &&
                patient.medicationsAndFollowUp.nextEchoDate >= startOfDay &&
                patient.medicationsAndFollowUp.nextEchoDate < endOfDay &&
                patient.medicationsAndFollowUp.followUpStatus == null
            }

            patientsWithFollowUpToday.forEach { patient ->
                sendReminderNotification(patient.demographics.name, "Follow-up")
            }

            patientsWithEchoToday.forEach { patient ->
                sendReminderNotification(patient.demographics.name, "Echo")
            }

            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private fun sendReminderNotification(patientName: String, type: String) {
        val channelId = DrRrpMessagingService.CHANNEL_ROUTINE
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Clinic Reminder")
            .setContentText("Reminder: $type scheduled today for $patientName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.notify("staff_reminder_${patientName}_$type".hashCode(), notification)
    }
}
