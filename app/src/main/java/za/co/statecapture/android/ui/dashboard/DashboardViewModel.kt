package za.co.statecapture.android.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import za.co.statecapture.android.data.PurchaseDao
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import za.co.statecapture.android.data.Purchase

data class MonthData(
    val monthName: String, // e.g. "Jan"
    val amountRand: Double,
    val kwhYield: Double
)

data class DashboardState(
    val thisMonthRand: Double = 0.0,
    val thisMonthKwh: Double = 0.0,
    val thisMonthPurchaseCount: Int = 0,
    val last12MonthsRand: Double = 0.0,
    val last12MonthsKwh: Double = 0.0,
    val allTimeRand: Double = 0.0,
    val allTimeKwh: Double = 0.0,
    // Averages based on the last 12 *complete* months (excluding the current month)
    val dailyAverageRand: Double = 0.0,
    val dailyAverageKwh: Double = 0.0,
    val weeklyAverageRand: Double = 0.0,
    val weeklyAverageKwh: Double = 0.0,
    val monthlyAverageRand: Double = 0.0,
    val monthlyAverageKwh: Double = 0.0,
    val averagesSubtitle: String = "Based on complete months with purchases",
    val monthlyHistory: List<MonthData> = emptyList(),
    val firstPurchaseTimestamp: Long? = null,
    val includeVat: Boolean = true
)

class DashboardViewModel(private val purchaseDao: PurchaseDao) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    init {
        observeStats()
    }

    fun onIncludeVatToggle(include: Boolean) {
        _uiState.update { it.copy(includeVat = include) }
        observeStats()
    }

    private fun observeStats() {
        // Start of the current month
        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Start of the last 12 complete months window (= start of the month 12 months ago)
        val startOf12CompleteMonths = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -12)
        }.timeInMillis

        // Last 12 months (including current, used for the graph & totals card)
        val last12Months = Calendar.getInstance().apply {
            add(Calendar.MONTH, -12)
        }.timeInMillis

        // Days in the last 12 complete months window (for daily average)
        val daysIn12CompleteMonths = ((startOfMonth - startOf12CompleteMonths) / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(1)

        viewModelScope.launch {
            val statsFlow = combine(
                purchaseDao.getTotalCentsSince(startOfMonth),
                purchaseDao.getTotalVatCentsSince(startOfMonth),
                purchaseDao.getTotalKwhSince(startOfMonth),
                purchaseDao.getTotalCentsSince(last12Months),
                purchaseDao.getTotalVatCentsSince(last12Months),
                purchaseDao.getTotalKwhSince(last12Months),
                purchaseDao.getAllTimeTotalCents(),
                purchaseDao.getAllTimeTotalVatCents(),
                purchaseDao.getAllTimeTotalKwh(),
                purchaseDao.getAllPurchases()
            ) { stats -> stats }

            combine(
                statsFlow,
                purchaseDao.getDistinctMonthCount()
            ) { stats, _ ->
                val includeVat = _uiState.value.includeVat
                val thisMonthTotal = (stats[0] as? Double ?: 0.0) + (if (includeVat) (stats[1] as? Double ?: 0.0) else 0.0)
                val thisMonthKwh = stats[2] as? Double ?: 0.0
                val last12Total = (stats[3] as? Double ?: 0.0) + (if (includeVat) (stats[4] as? Double ?: 0.0) else 0.0)
                val last12Kwh = stats[5] as? Double ?: 0.0
                val allTimeTotal = (stats[6] as? Double ?: 0.0) + (if (includeVat) (stats[7] as? Double ?: 0.0) else 0.0)
                val allTimeKwh = stats[8] as? Double ?: 0.0

                @Suppress("UNCHECKED_CAST")
                val allPurchases = stats[9] as List<Purchase>

                // Count purchases made in current month
                val thisMonthCount = allPurchases.count { p ->
                    p.timestamp >= startOfMonth
                }

                // Build last 12 months history (for the graph — includes current month)
                val history = mutableListOf<MonthData>()
                for (i in 11 downTo 0) {
                    val targetMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -i) }
                    val year = targetMonth.get(Calendar.YEAR)
                    val month = targetMonth.get(Calendar.MONTH)
                    val monthPurchases = allPurchases.filter { p ->
                        val pCal = Calendar.getInstance().apply { timeInMillis = p.timestamp }
                        pCal.get(Calendar.YEAR) == year && pCal.get(Calendar.MONTH) == month
                    }
                    val amount = monthPurchases.sumOf { it.amountCents } + (if (includeVat) monthPurchases.sumOf { it.vatAmountCents } else 0.0)
                    val kwh = monthPurchases.sumOf { it.kwhYield }
                    val monthName = SimpleDateFormat("MMM", Locale.US).format(targetMonth.time)
                    history.add(MonthData(monthName, amount / 100.0, kwh))
                }

                // Averages based on last 12 *complete* months only (exclude current month)
                val completePurchases = allPurchases.filter { it.timestamp >= startOf12CompleteMonths && it.timestamp < startOfMonth }
                val completeMonthsTotal = completePurchases.sumOf { it.amountCents } + (if (includeVat) completePurchases.sumOf { it.vatAmountCents } else 0.0)
                val completeMonthsKwh = completePurchases.sumOf { it.kwhYield }

                // Count distinct (year, month) pairs that have purchases in the complete window
                val distinctCompleteMonthsMap = completePurchases
                    .map { p ->
                        val c = Calendar.getInstance().apply { timeInMillis = p.timestamp }
                        Pair(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
                    }
                    .toSet()

                val distinctMonthCount = distinctCompleteMonthsMap.size

                // Total number of days only across the months that contain data
                val totalDaysWithData = if (distinctMonthCount > 0) {
                    distinctCompleteMonthsMap.sumOf { (year, month) ->
                        Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }.getActualMaximum(Calendar.DAY_OF_MONTH)
                    }
                } else {
                    1
                }

                val effectiveMonthCount = distinctMonthCount.coerceAtLeast(1)

                val monthlyAvgRand = if (distinctMonthCount > 0) (completeMonthsTotal / 100.0) / effectiveMonthCount else 0.0
                val monthlyAvgKwh = if (distinctMonthCount > 0) completeMonthsKwh / effectiveMonthCount else 0.0
                val dailyAvgRand = if (distinctMonthCount > 0) (completeMonthsTotal / 100.0) / totalDaysWithData else 0.0
                val dailyAvgKwh = if (distinctMonthCount > 0) completeMonthsKwh / totalDaysWithData else 0.0
                val weeklyAvgRand = dailyAvgRand * 7.0
                val weeklyAvgKwh = dailyAvgKwh * 7.0

                val averagesSubtitle = when (distinctMonthCount) {
                    0 -> "No previous complete months with purchases"
                    1 -> "Based on 1 complete month with purchases"
                    else -> "Based on $distinctMonthCount complete months with purchases"
                }

                val firstTimestamp = allPurchases.minOfOrNull { it.timestamp }

                DashboardState(
                    thisMonthRand = thisMonthTotal / 100.0,
                    thisMonthKwh = thisMonthKwh,
                    thisMonthPurchaseCount = thisMonthCount,
                    last12MonthsRand = last12Total / 100.0,
                    last12MonthsKwh = last12Kwh,
                    allTimeRand = allTimeTotal / 100.0,
                    allTimeKwh = allTimeKwh,
                    dailyAverageRand = dailyAvgRand,
                    dailyAverageKwh = dailyAvgKwh,
                    weeklyAverageRand = weeklyAvgRand,
                    weeklyAverageKwh = weeklyAvgKwh,
                    monthlyAverageRand = monthlyAvgRand,
                    monthlyAverageKwh = monthlyAvgKwh,
                    averagesSubtitle = averagesSubtitle,
                    monthlyHistory = history,
                    firstPurchaseTimestamp = firstTimestamp,
                    includeVat = includeVat
                )
            }.collect { state ->
                _uiState.update { state }
            }
        }
    }
}
