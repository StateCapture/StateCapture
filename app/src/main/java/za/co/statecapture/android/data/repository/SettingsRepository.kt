package za.co.statecapture.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_DAY = intPreferencesKey("reminder_day")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data
        .map { preferences ->
            UserSettings(
                remindersEnabled = preferences[PreferencesKeys.REMINDERS_ENABLED] ?: false,
                reminderDay = preferences[PreferencesKeys.REMINDER_DAY] ?: 1,
                reminderHour = preferences[PreferencesKeys.REMINDER_HOUR] ?: 9,
                reminderMinute = preferences[PreferencesKeys.REMINDER_MINUTE] ?: 0
            )
        }

    suspend fun updateRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REMINDERS_ENABLED] = enabled
        }
    }

    suspend fun updateReminderTime(day: Int, hour: Int, minute: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REMINDER_DAY] = day
            preferences[PreferencesKeys.REMINDER_HOUR] = hour
            preferences[PreferencesKeys.REMINDER_MINUTE] = minute
        }
    }
}

data class UserSettings(
    val remindersEnabled: Boolean,
    val reminderDay: Int,
    val reminderHour: Int,
    val reminderMinute: Int
)
