package za.co.statecapture.android.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import za.co.statecapture.android.data.repository.SettingsRepository
import za.co.statecapture.android.data.repository.UserSettings
import za.co.statecapture.android.notification.NotificationScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<UserSettings> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings(false, 1, 9, 0)
        )

    fun toggleReminders(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateRemindersEnabled(enabled)
            if (enabled) {
                val current = settings.value
                NotificationScheduler.scheduleReminder(context, current.reminderDay, current.reminderHour, current.reminderMinute)
            } else {
                NotificationScheduler.cancelReminder(context)
            }
        }
    }

    fun updateTime(context: Context, day: Int, hour: Int, minute: Int) {
        viewModelScope.launch {
            repository.updateReminderTime(day, hour, minute)
            if (settings.value.remindersEnabled) {
                NotificationScheduler.scheduleReminder(context, day, hour, minute)
            }
        }
    }
}
