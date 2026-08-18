package za.co.statecapture.android.data.migration

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import za.co.statecapture.android.data.Reminder
import za.co.statecapture.android.data.ReminderDao
import za.co.statecapture.android.data.ReminderFrequency
import za.co.statecapture.android.notification.NotificationScheduler
import kotlinx.coroutines.flow.firstOrNull

// Access the same DataStore that SettingsRepository previously used
private val Context.legacySettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * One-time migration: reads the old single-reminder DataStore entry (from SettingsRepository)
 * and inserts it into the new Room-based reminders table.
 *
 * A `reminders_migrated_to_room` flag is written to DataStore after the first successful
 * migration run so this logic is never executed again.
 */
object ReminderMigrationHelper {

    private val KEY_REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
    private val KEY_REMINDER_DAY = intPreferencesKey("reminder_day")
    private val KEY_REMINDER_HOUR = intPreferencesKey("reminder_hour")
    private val KEY_REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    private val KEY_MIGRATED = booleanPreferencesKey("reminders_migrated_to_room")

    suspend fun migrateIfNeeded(context: Context, reminderDao: ReminderDao) {
        val prefs = context.legacySettingsDataStore.data.firstOrNull() ?: return

        // Already migrated — nothing to do
        if (prefs[KEY_MIGRATED] == true) return

        val wasEnabled = prefs[KEY_REMINDERS_ENABLED] ?: false
        val day = prefs[KEY_REMINDER_DAY] ?: 1
        val hour = prefs[KEY_REMINDER_HOUR] ?: 9
        val minute = prefs[KEY_REMINDER_MINUTE] ?: 0

        if (wasEnabled) {
            val reminder = Reminder(
                frequency = ReminderFrequency.MONTHLY,
                dayValue = day,
                hour = hour,
                minute = minute,
                isEnabled = true
            )
            val id = reminderDao.insert(reminder)
            NotificationScheduler.scheduleReminder(context, reminder.copy(id = id))
        }

        // Mark migration as done
        context.legacySettingsDataStore.edit { it[KEY_MIGRATED] = true }
    }
}
