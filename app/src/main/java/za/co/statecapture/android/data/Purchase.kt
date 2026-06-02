package za.co.statecapture.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = Meter::class,
            parentColumns = ["id"],
            childColumns = ["meterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["meterId"])]
)
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meterId: Int,
    val amountCents: Double,
    val vatAmountCents: Double = 0.0,
    val kwhYield: Double,
    val timestamp: Long = System.currentTimeMillis()
)
