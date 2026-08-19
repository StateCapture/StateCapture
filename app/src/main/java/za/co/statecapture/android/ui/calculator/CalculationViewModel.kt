package za.co.statecapture.android.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import za.co.statecapture.android.data.Purchase
import za.co.statecapture.android.data.PurchaseDao
import za.co.statecapture.android.data.repository.TariffRepository
import za.co.statecapture.android.domain.engine.CalculationResult
import za.co.statecapture.android.domain.engine.TariffCalculator
import za.co.statecapture.android.domain.model.TariffProvider
import za.co.statecapture.android.domain.model.TariffIndexItem
import za.co.statecapture.android.util.AppConstants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import za.co.statecapture.android.data.Meter
import za.co.statecapture.android.domain.engine.BlockYield

enum class CalculationMode {
    RandsToKwh,
    KwhToRands
}

enum class SortField { DATE, AMOUNT, UNITS }
enum class SortDirection { ASC, DESC }
enum class WarningSeverity { INFO, WARNING, ALERT }

data class SmartWarning(
    val title: String,
    val message: String,
    val severity: WarningSeverity = WarningSeverity.INFO
)

class CalculationViewModel(
    private val repository: TariffRepository,
    private val purchaseDao: PurchaseDao
) : ViewModel() {

    private val calculator = TariffCalculator()

    private val _uiState = MutableStateFlow(CalculationUiState())
    val uiState: StateFlow<CalculationUiState> = _uiState.asStateFlow()

    init {
        loadTariffData()
    }

    private fun loadTariffData() {
        viewModelScope.launch {
            try {
                val providers = repository.getAllProviders()
                _uiState.update { it.copy(providers = providers) }
                if (providers.isNotEmpty() && _uiState.value.selectedMeter == null) {
                    onProviderSelected(providers.first())
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to load tariffs: ${e.message}") }
            }
        }
    }

    fun setMeter(meter: Meter) {
        viewModelScope.launch {
            val provider = repository.getProvider(meter.providerId)
            val activeFixedCharge = if (provider != null) {
                val today = LocalDate.now()
                provider.periods.find { p ->
                    val from = LocalDate.parse(p.validFrom)
                    val to = p.validTo?.let { LocalDate.parse(it) }
                    (!today.isBefore(from)) && (to == null || !today.isAfter(to))
                }?.fixedMonthlyChargeCents ?: provider.periods.lastOrNull()?.fixedMonthlyChargeCents ?: 0
            } else 0
            _uiState.update {
                it.copy(
                    selectedMeter = meter,
                    selectedProvider = provider,
                    fixedMonthlyChargeCents = activeFixedCharge,
                    inputAmount = "",
                    selectedYearMonth = YearMonth.now()
                )
            }
            updateMonthlyTotal()
        }
    }

    fun clearMeter() {
        _uiState.update {
            it.copy(
                selectedMeter = null,
                inputAmount = "",
                monthlyCumulativeKwh = 0.0,
                cumulativeBreakdown = emptyList(),
                result = null,
                selectedYearMonth = YearMonth.now()
            )
        }
    }

    fun onInputAmountChange(amount: String) {
        val filteredAmount = amount.filter { it.isDigit() }
        val parsed = filteredAmount.toDoubleOrNull()
        // Prevent stupidly large values (limit to 100,000)
        if (parsed != null && parsed > 100_000.0) return
        // Prevent overly long inputs (e.g. many leading zeros)
        if (filteredAmount.length > 10) return

        _uiState.update { it.copy(inputAmount = filteredAmount) }
        calculateResult()
    }

    fun onModeChange(mode: CalculationMode) {
        _uiState.update { it.copy(mode = mode) }
        calculateResult()
    }

    fun onIncludeVatChange(includeVat: Boolean) {
        _uiState.update { it.copy(includeVat = includeVat) }
        calculateResult()
    }

    fun onBlockShortcutClick(targetCumulativeKwh: Double) {
        val currentKwh = _uiState.value.monthlyCumulativeKwh
        val neededKwh = (targetCumulativeKwh - currentKwh).coerceAtLeast(0.0)
        
        if (neededKwh > AppConstants.BLOCK_EXHAUSTION_TOLERANCE_KWH) {
            _uiState.update { 
                it.copy(
                    mode = CalculationMode.KwhToRands,
                    inputAmount = String.format(Locale.US, "%.1f", neededKwh)
                )
            }
            calculateResult()
        }
    }

    fun onProviderSelected(indexItem: TariffIndexItem) {
        _uiState.update { it.copy(selectedIndexItem = indexItem) }
        viewModelScope.launch {
            try {
                val provider = repository.getProvider(indexItem.id)
                if (provider != null) {
                    val today = LocalDate.now()
                    val activeFixedCharge = provider.periods.find { p ->
                        val from = LocalDate.parse(p.validFrom)
                        val to = p.validTo?.let { LocalDate.parse(it) }
                        (!today.isBefore(from)) && (to == null || !today.isAfter(to))
                    }?.fixedMonthlyChargeCents ?: provider.periods.lastOrNull()?.fixedMonthlyChargeCents ?: 0
                    _uiState.update { it.copy(selectedProvider = provider, fixedMonthlyChargeCents = activeFixedCharge) }
                    if (_uiState.value.selectedMeter != null) {
                        updateMonthlyTotal()
                    } else {
                        calculateResult()
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to load provider: ${e.message}") }
            }
        }
    }
    
    fun setProviderDirectly(provider: TariffProvider) {
        // Called from the Tariffs screen's "Try it out" section via its own dedicated
        // CalculationViewModel instance (not shared with the Meter Calculator screen).
        // Reset cumulative state so calculations always start fresh at Block 1.
        val today = LocalDate.now()
        val activeFixedCharge = provider.periods.find { p ->
            val from = java.time.LocalDate.parse(p.validFrom)
            val to = p.validTo?.let { java.time.LocalDate.parse(it) }
            (!today.isBefore(from)) && (to == null || !today.isAfter(to))
        }?.fixedMonthlyChargeCents ?: provider.periods.lastOrNull()?.fixedMonthlyChargeCents ?: 0
        _uiState.update {
            it.copy(
                selectedProvider = provider,
                selectedIndexItem = null,
                monthlyCumulativeKwh = 0.0,
                monthlyCumulativeAmountCents = 0.0,
                monthlyCumulativeVatCents = 0.0,
                cumulativeBreakdown = emptyList(),
                result = null,
                inputAmount = "",
                fixedMonthlyChargeCents = activeFixedCharge
            )
        }
    }

    // ── Month navigation ─────────────────────────────────────────────────────

    fun goToPreviousMonth() {
        val prev = _uiState.value.selectedYearMonth.minusMonths(1)
        _uiState.update { it.copy(selectedYearMonth = prev) }
        updateMonthlyTotal()
    }

    fun goToNextMonth() {
        val next = _uiState.value.selectedYearMonth.plusMonths(1)
        if (next <= YearMonth.now()) {
            _uiState.update { it.copy(selectedYearMonth = next) }
            updateMonthlyTotal()
        }
    }

    // ── Sort ─────────────────────────────────────────────────────────────────

    fun onSortChange(field: SortField) {
        val current = _uiState.value
        val newDirection = if (current.sortField == field && current.sortDirection == SortDirection.DESC) {
            SortDirection.ASC
        } else {
            SortDirection.DESC
        }
        val sorted = sortPurchases(current.recentPurchases, field, newDirection)
        _uiState.update { it.copy(sortField = field, sortDirection = newDirection, recentPurchases = sorted) }
    }

    // ── Purchase CRUD ─────────────────────────────────────────────────────────

    fun deletePurchase(purchaseId: Long) {
        viewModelScope.launch {
            purchaseDao.delete(purchaseId)
            updateMonthlyTotal()
        }
    }

    fun editPurchase(purchase: Purchase) {
        viewModelScope.launch {
            purchaseDao.update(purchase)
            updateMonthlyTotal()
        }
    }

    fun savePurchase(date: LocalDate = LocalDate.now()) {
        val result = _uiState.value.result ?: return
        val meter = _uiState.value.selectedMeter ?: return
        
        if (result.result.totalCostCents < 0 || result.result.totalKwh <= 0) return

        val timestamp = if (date == LocalDate.now()) {
            System.currentTimeMillis()
        } else {
            date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        viewModelScope.launch {
            purchaseDao.insert(
                Purchase(
                    meterId = meter.id,
                    amountCents = result.result.totalCostCents,
                    vatAmountCents = result.vatAmountCents,
                    kwhYield = result.result.totalKwh,
                    timestamp = timestamp
                )
            )
            _uiState.update { it.copy(inputAmount = "") }
            updateMonthlyTotal()
        }
    }

    fun claimFreeBlock(date: LocalDate = LocalDate.now()) {
        val meter = _uiState.value.selectedMeter ?: return
        val provider = _uiState.value.selectedProvider ?: return
        val ym = _uiState.value.selectedYearMonth
        val isCurrentMonth = ym == YearMonth.now()
        val referenceDate = if (isCurrentMonth) date else ym.atEndOfMonth()
        
        viewModelScope.launch {
            val result = calculator.calculateYield(provider, 0.0, _uiState.value.monthlyCumulativeKwh, referenceDate)
            if (result.totalKwh > 0) {
                val timestamp = if (date == LocalDate.now()) {
                    System.currentTimeMillis()
                } else {
                    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                purchaseDao.insert(
                    Purchase(
                        meterId = meter.id,
                        amountCents = 0.0,
                        vatAmountCents = 0.0,
                        kwhYield = result.totalKwh,
                        timestamp = timestamp
                    )
                )
                updateMonthlyTotal()
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun sortPurchases(
        purchases: List<Purchase>,
        field: SortField,
        direction: SortDirection
    ): List<Purchase> {
        val comparator: Comparator<Purchase> = when (field) {
            SortField.DATE   -> compareBy<Purchase> { it.timestamp }.thenBy { it.id }
            SortField.AMOUNT -> compareBy<Purchase> { it.amountCents + it.vatAmountCents }.thenBy { it.id }
            SortField.UNITS  -> compareBy<Purchase> { it.kwhYield }.thenBy { it.id }
        }
        return if (direction == SortDirection.DESC) purchases.sortedWith(comparator.reversed())
        else purchases.sortedWith(comparator)
    }

    private fun updateMonthlyTotal() {
        val state = _uiState.value
        val meter = state.selectedMeter ?: return
        val provider = state.selectedProvider ?: return

        val ym = state.selectedYearMonth
        viewModelScope.launch {
            val startOfMonth = ym.atDay(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val startOfNextMonth = ym.plusMonths(1).atDay(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val isCurrentMonth = ym == YearMonth.now()
            val totalKwh = if (isCurrentMonth) {
                purchaseDao.getMonthlyTotalKwh(meter.id, startOfMonth) ?: 0.0
            } else {
                purchaseDao.getMonthlyTotalKwhBetween(meter.id, startOfMonth, startOfNextMonth) ?: 0.0
            }

            val referenceDate = if (isCurrentMonth) LocalDate.now() else ym.atEndOfMonth()

            // Ensure the provider has tariff periods for the reference date.
            // For historical months from a previous financial year the repository will
            // download the matching tariff file on demand and merge its periods into the
            // locally-cached provider, giving us the correct block boundaries.
            val effectiveProvider = repository.getProvider(meter.providerId, referenceDate) ?: provider
            if (effectiveProvider !== provider) {
                _uiState.update { it.copy(selectedProvider = effectiveProvider) }
            }

            val breakdown = calculator.calculateCumulativeBreakdown(effectiveProvider, totalKwh, referenceDate)

            val activeFixedCharge = effectiveProvider.periods.find { p ->
                val from = LocalDate.parse(p.validFrom)
                val to = p.validTo?.let { LocalDate.parse(it) }
                (!referenceDate.isBefore(from)) && (to == null || !referenceDate.isAfter(to))
            }?.fixedMonthlyChargeCents ?: effectiveProvider.periods.lastOrNull()?.fixedMonthlyChargeCents ?: 0

            val allHistory = purchaseDao.getPurchasesForMeter(meter.id).first()

            val monthHistory = allHistory.filter {
                it.timestamp >= startOfMonth && it.timestamp < startOfNextMonth
            }
            val totalAmountCents = monthHistory.sumOf { it.amountCents }
            val totalVatCents = monthHistory.sumOf { it.vatAmountCents }
            val sorted = sortPurchases(monthHistory, state.sortField, state.sortDirection)

            val freeYieldResult = calculator.calculateYield(effectiveProvider, 0.0, totalKwh, referenceDate)
            val availableFreeKwh = freeYieldResult.totalKwh

            _uiState.update {
                it.copy(
                    monthlyCumulativeKwh = totalKwh,
                    monthlyCumulativeAmountCents = totalAmountCents,
                    monthlyCumulativeVatCents = totalVatCents,
                    cumulativeBreakdown = breakdown,
                    recentPurchases = sorted,
                    availableFreeKwh = availableFreeKwh,
                    fixedMonthlyChargeCents = activeFixedCharge
                )
            }
            calculateResult()
        }
    }

    private fun calculateResult() {
        viewModelScope.launch {
            val state = _uiState.value
            val provider = state.selectedProvider ?: return@launch
            val input = state.inputAmount.toDoubleOrNull() ?: run {
                _uiState.update { it.copy(result = null) }
                return@launch
            }

            if (input <= 0) {
                _uiState.update { it.copy(result = null) }
                return@launch
            }

            val isCurrentMonth = state.selectedYearMonth == YearMonth.now()
            val referenceDate = if (isCurrentMonth) LocalDate.now() else state.selectedYearMonth.atEndOfMonth()

            // For the Tariffs screen (no meter) the fixed charge is always shown fresh.
            // For the Meter screen, only include it on the first purchase of the month.
            val fixedChargeAlreadyPaid = state.monthlyCumulativeKwh > 0
            val result = if (state.mode == CalculationMode.RandsToKwh) {
                val baseInput = if (state.includeVat) {
                    (input * 100.0 / AppConstants.VAT_MULTIPLIER) / 100.0
                } else {
                    input
                }
                calculator.calculateYield(
                    provider = provider,
                    purchaseAmountCents = baseInput * 100.0,
                    previousPurchasesKwh = state.monthlyCumulativeKwh,
                    date = referenceDate
                )
            } else {
                calculator.calculateCost(
                    provider = provider,
                    targetKwh = input,
                    previousPurchasesKwh = state.monthlyCumulativeKwh,
                    date = referenceDate,
                    includeFixedCharge = !fixedChargeAlreadyPaid
                )
            }

            val vatAmountCents = Math.round(result.totalCostCents * AppConstants.VAT_RATE).toDouble()
            val displayResult = CalculationDisplayResult(
                result = result,
                vatAmountCents = vatAmountCents,
                includeVat = state.includeVat,
                mode = state.mode,
                fixedChargeAlreadyPaid = state.monthlyCumulativeKwh > 0
            )
            
            val warnings = generateSmartWarnings(state, displayResult)
            _uiState.update { it.copy(result = displayResult, smartWarnings = warnings) }
        }
    }

    private fun generateSmartWarnings(
        state: CalculationUiState,
        displayResult: CalculationDisplayResult
    ): List<SmartWarning> {
        val warnings = mutableListOf<SmartWarning>()
        val provider = state.selectedProvider ?: return emptyList()
        val blocks = provider.periods.lastOrNull()?.blocks ?: return emptyList()
        if (blocks.size <= 1 || state.selectedYearMonth != YearMonth.now()) return emptyList()

        val now = LocalDate.now()
        val daysInMonth = now.lengthOfMonth()
        val isLastWeek = now.dayOfMonth > daysInMonth - 7
        val currentKwh = state.monthlyCumulativeKwh
        
        val firstBlock = blocks.first()
        val exhaustedFirstBlock = currentKwh >= firstBlock.maxKwh

        // 1. End-of-month trap
        if (isLastWeek && exhaustedFirstBlock) {
            val daysLeft = daysInMonth - now.dayOfMonth + 1
            val nextMonth = now.plusMonths(1).month.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            warnings.add(SmartWarning(
                title = "End-of-Month Strategy",
                message = "You've used your cheapest units for this month. Since blocks reset on 1 $nextMonth ($daysLeft days), consider buying only what you need to bridge the gap.",
                severity = WarningSeverity.WARNING
            ))
        }

        // 2. Highest tier reached
        val currentBlockIndex = blocks.indexOfFirst { currentKwh < it.maxKwh }
        if (currentBlockIndex == blocks.size - 1 || (currentBlockIndex == -1 && blocks.isNotEmpty())) {
            warnings.add(SmartWarning(
                title = "Maximum Tariff Reached",
                message = "You've reached the highest block (Block ${blocks.size}). Every unit you buy now costs the maximum rate. Avoid 'stocking up' until next month.",
                severity = WarningSeverity.ALERT
            ))
        }

        // 3. Significant jump warning
        val endKwh = currentKwh + displayResult.result.totalKwh
        val startBlockIdx = blocks.indexOfFirst { currentKwh < it.maxKwh }.coerceAtLeast(0)
        val endBlockIdx = blocks.indexOfFirst { endKwh < it.maxKwh }
        
        if (endBlockIdx > startBlockIdx && endBlockIdx != -1) {
            val nextRate = blocks[endBlockIdx].ratePerKwhCents
            val currRate = blocks[startBlockIdx].ratePerKwhCents
            
            if (currRate == 0.0 && nextRate > 0.0) {
                warnings.add(SmartWarning(
                    title = "Price Jump Detected",
                    message = "This purchase pushes you into Block ${endBlockIdx + 1}, where units are no longer free!",
                    severity = WarningSeverity.WARNING
                ))
            } else if (currRate > 0.0) {
                val jumpPercent = (((nextRate - currRate) / currRate) * 100).toInt()
                
                if (jumpPercent > 10) {
                    warnings.add(SmartWarning(
                        title = "Price Jump Detected",
                        message = "This purchase pushes you into Block ${endBlockIdx + 1}, where units are $jumpPercent% more expensive!",
                        severity = WarningSeverity.WARNING
                    ))
                }
            }
        }

        return warnings
    }
}

data class CalculationDisplayResult(
    val result: CalculationResult,
    val vatAmountCents: Double,
    val includeVat: Boolean,
    val mode: CalculationMode,
    val fixedChargeAlreadyPaid: Boolean
)

data class CalculationUiState(
    val inputAmount: String = "",
    val mode: CalculationMode = CalculationMode.RandsToKwh,
    val includeVat: Boolean = true,
    val providers: List<TariffIndexItem> = emptyList(),
    val selectedIndexItem: TariffIndexItem? = null,
    val selectedProvider: TariffProvider? = null,
    val selectedMeter: Meter? = null,
    val result: CalculationDisplayResult? = null,
    val monthlyCumulativeKwh: Double = 0.0,
    val monthlyCumulativeAmountCents: Double = 0.0,
    val monthlyCumulativeVatCents: Double = 0.0,
    val cumulativeBreakdown: List<BlockYield> = emptyList(),
    val recentPurchases: List<Purchase> = emptyList(),
    val errorMessage: String? = null,
    // Month navigation
    val selectedYearMonth: YearMonth = YearMonth.now(),
    // Sort
    val sortField: SortField = SortField.DATE,
    val sortDirection: SortDirection = SortDirection.DESC,
    val smartWarnings: List<SmartWarning> = emptyList(),
    val availableFreeKwh: Double = 0.0,
    // Fixed monthly charge for the selected provider's active period (0 if none)
    val fixedMonthlyChargeCents: Int = 0
)
