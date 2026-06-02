package za.co.statecapture.android.data

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import za.co.statecapture.android.domain.model.TariffPeriod

class TariffConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromPeriodList(value: List<TariffPeriod>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toPeriodList(value: String): List<TariffPeriod> {
        return json.decodeFromString(value)
    }
}
