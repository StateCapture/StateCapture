package za.co.statecapture.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import za.co.statecapture.android.domain.model.TariffPeriod

@Entity(tableName = "tariff_providers")
data class TariffProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val color: String?,
    val periods: List<TariffPeriod>
)
