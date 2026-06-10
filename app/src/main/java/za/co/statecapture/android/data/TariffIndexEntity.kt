package za.co.statecapture.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import za.co.statecapture.android.domain.model.TariffIndexFileItem

@Entity(tableName = "tariff_index")
data class TariffIndexEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val color: String?,
    val providerId: String,
    val files: List<TariffIndexFileItem>
)
