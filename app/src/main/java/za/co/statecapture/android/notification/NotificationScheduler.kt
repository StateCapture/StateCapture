package za.co.statecapture.android.notification

import android.content.Context
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit
import za.co.statecapture.android.data.Reminder
import za.co.statecapture.android.data.ReminderFrequency

object NotificationScheduler {

    private fun getWorkName(id: Long): String = "reminder_work_$id"

    fun scheduleReminder(context: Context, reminder: Reminder) {
        val workManager = WorkManager.getInstance(context)
        
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        
        when (reminder.frequency) {
            ReminderFrequency.MONTHLY -> {
                calendar.set(Calendar.DAY_OF_MONTH, reminder.dayValue)
                calendar.set(Calendar.HOUR_OF_DAY, reminder.hour)
                calendar.set(Calendar.MINUTE, reminder.minute)
                calendar.set(Calendar.SECOND, 0)
                
                if (calendar.timeInMillis <= now) {
                    calendar.add(Calendar.MONTH, 1)
                }
            }
            ReminderFrequency.WEEKLY -> {
                calendar.set(Calendar.DAY_OF_WEEK, reminder.dayValue)
                calendar.set(Calendar.HOUR_OF_DAY, reminder.hour)
                calendar.set(Calendar.MINUTE, reminder.minute)
                calendar.set(Calendar.SECOND, 0)
                
                if (calendar.timeInMillis <= now) {
                    calendar.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
        }
        
        val delay = calendar.timeInMillis - now
        
        val inputData = workDataOf(
            "reminder_id" to reminder.id
        )
        
        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag(getWorkName(reminder.id))
            .build()
            
        workManager.enqueueUniqueWork(
            getWorkName(reminder.id),
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelReminder(context: Context, id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(getWorkName(id))
    }
}
