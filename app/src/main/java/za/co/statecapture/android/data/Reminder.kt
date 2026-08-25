package za.co.statecapture.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReminderFrequency {
    MONTHLY,
    WEEKLY
}

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val frequency: ReminderFrequency,
    val dayValue: Int, // 1-28 for MONTHLY, 1-7 (Calendar.SUNDAY to Calendar.SATURDAY) for WEEKLY
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true
)
