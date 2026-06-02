package za.co.statecapture.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meters")
data class Meter(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val meterNumber: String,
    val providerId: String, // e.g., "eskom_homelight_20A"
    val isDefault: Boolean = false,
    val icon: String = "⚡", // Default emoji
    val displayOrder: Int = 0
)
