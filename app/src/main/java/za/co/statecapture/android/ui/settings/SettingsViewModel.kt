package za.co.statecapture.android.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import za.co.statecapture.android.data.Reminder
import za.co.statecapture.android.data.ReminderDao
import za.co.statecapture.android.notification.NotificationScheduler

class SettingsViewModel(private val reminderDao: ReminderDao) : ViewModel() {

    val reminders: StateFlow<List<Reminder>> = reminderDao.getAllReminders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addReminder(context: Context, reminder: Reminder) {
        viewModelScope.launch {
            val id = reminderDao.insert(reminder)
            if (reminder.isEnabled) {
                NotificationScheduler.scheduleReminder(context, reminder.copy(id = id))
            }
        }
    }

    fun updateReminder(context: Context, reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.update(reminder)
            if (reminder.isEnabled) {
                NotificationScheduler.scheduleReminder(context, reminder)
            } else {
                NotificationScheduler.cancelReminder(context, reminder.id)
            }
        }
    }

    fun deleteReminder(context: Context, reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.delete(reminder)
            NotificationScheduler.cancelReminder(context, reminder.id)
        }
    }
}
