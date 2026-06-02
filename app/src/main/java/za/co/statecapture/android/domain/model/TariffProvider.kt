package za.co.statecapture.android.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class TariffBlock(
    @SerialName("min_kwh") val minKwh: Int,
    @SerialName("max_kwh") val maxKwh: Int,
    @SerialName("rate_per_kwh_cents") val ratePerKwhCents: Double
)

@Serializable
data class TariffPeriod(
    @SerialName("valid_from") val validFrom: String,
    @SerialName("valid_to") val validTo: String?,
    val blocks: List<TariffBlock>,
    @SerialName("fixed_monthly_charge_cents") val fixedMonthlyChargeCents: Int
)

@Serializable
data class TariffProvider(
    val id: String,
    val name: String,
    val type: String, // "eskom" or "municipality"
    val color: String? = null,
    val periods: List<TariffPeriod>
)

@Serializable
data class TariffData(
    val version: String,
    @SerialName("last_updated") val lastUpdated: String,
    val providers: List<TariffProvider>
)
