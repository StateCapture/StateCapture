package za.co.statecapture.android.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import za.co.statecapture.android.MainActivity
import za.co.statecapture.android.data.AppDatabase
import android.R as AndroidR

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong("reminder_id", -1L)
        if (reminderId == -1L) return Result.success()

        val db = AppDatabase.getDatabase(applicationContext)
        val reminder = db.reminderDao().getReminderById(reminderId)

        if (reminder != null && reminder.isEnabled) {
            sendNotification()
            // Reschedule for next occurrence
            NotificationScheduler.scheduleReminder(applicationContext, reminder)
        }

        return Result.success()
    }

    private fun sendNotification() {
        val channelId = "monthly_reminder"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Purchase Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to capture your electricity purchases"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Build a PendingIntent that opens the app on the Dashboard screen
        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigateTo", "dashboard")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(AndroidR.drawable.ic_dialog_info) // Fallback icon
            .setContentTitle("Electricity Purchase Reminder")
            .setContentText("Remember to track your block tariffs accurately by recording your electricity purchase.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }
}
