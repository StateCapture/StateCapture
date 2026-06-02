package za.co.statecapture.android.notification

import android.content.Context
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    private const val WORK_NAME = "monthly_reminder_work"

    fun scheduleReminder(context: Context, day: Int, hour: Int, minute: Int) {
        val workManager = WorkManager.getInstance(context)
        
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        
        // Set to desired time
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        
        // If time has passed, move to next month
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.MONTH, 1)
        }
        
        val delay = calendar.timeInMillis - now
        
        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()
            
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
