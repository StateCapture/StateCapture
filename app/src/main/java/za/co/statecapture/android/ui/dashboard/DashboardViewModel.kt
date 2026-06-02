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
    val last12MonthsRand: Double = 0.0,
    val last12MonthsKwh: Double = 0.0,
    val allTimeRand: Double = 0.0,
    val allTimeKwh: Double = 0.0,
    val monthlyAverageRand: Double = 0.0,
    val monthlyAverageKwh: Double = 0.0,
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
        // This Month
        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Last 12 Months
        val last12Months = Calendar.getInstance().apply {
            add(Calendar.MONTH, -12)
        }.timeInMillis

        viewModelScope.launch {
            // Combine the first 9 raw stat flows + all purchases
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

            // Combine the stats array with the distinct-month count
            combine(
                statsFlow,
                purchaseDao.getDistinctMonthCount()
            ) { stats, distinctMonths ->
                val includeVat = _uiState.value.includeVat
                val thisMonthTotal = (stats[0] as? Double ?: 0.0) + (if (includeVat) (stats[1] as? Double ?: 0.0) else 0.0)
                val thisMonthKwh = stats[2] as? Double ?: 0.0
                val last12Total = (stats[3] as? Double ?: 0.0) + (if (includeVat) (stats[4] as? Double ?: 0.0) else 0.0)
                val last12Kwh = stats[5] as? Double ?: 0.0
                val allTimeTotal = (stats[6] as? Double ?: 0.0) + (if (includeVat) (stats[7] as? Double ?: 0.0) else 0.0)
                val allTimeKwh = stats[8] as? Double ?: 0.0
                
                @Suppress("UNCHECKED_CAST")
                val allPurchases = stats[9] as List<Purchase>

                // Calculate last 12 months breakdown
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

                val firstTimestamp = allPurchases.minOfOrNull { it.timestamp }

                // Use the count of distinct calendar months so that
                // a brand-new purchase on 1 May correctly divides by 1,
                // and in future months divides by the true number of months with data.
                val monthCount = maxOf(1, distinctMonths ?: 1)

                DashboardState(
                    thisMonthRand = thisMonthTotal / 100.0,
                    thisMonthKwh = thisMonthKwh,
                    last12MonthsRand = last12Total / 100.0,
                    last12MonthsKwh = last12Kwh,
                    allTimeRand = allTimeTotal / 100.0,
                    allTimeKwh = allTimeKwh,
                    monthlyAverageRand = (allTimeTotal / 100.0) / monthCount,
                    monthlyAverageKwh = allTimeKwh / monthCount,
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
