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
    val officialUrl: String? = null,
    val periods: List<TariffPeriod>
)

@Serializable
data class TariffIndexFileItem(
    @SerialName("valid_from") val validFrom: String,
    @SerialName("valid_to") val validTo: String?,
    val path: String
)

@Serializable
data class TariffIndexItem(
    val id: String,
    val name: String,
    val type: String,
    val color: String? = null,
    @SerialName("provider_id") val providerId: String,
    val files: List<TariffIndexFileItem>
)

@Serializable
data class IndexResponse(
    @SerialName("last_updated") val lastUpdated: String,
    val plans: List<TariffIndexItem>
)

@Serializable
data class TariffProviderFile(
    val id: String,
    val name: String,
    @SerialName("official_url") val officialUrl: String? = null,
    val tariffs: List<TariffProvider>
)
