package za.co.statecapture.android.ui.calculator

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import za.co.statecapture.android.data.Meter
import za.co.statecapture.android.data.Purchase
import za.co.statecapture.android.data.PurchaseDao
import za.co.statecapture.android.data.repository.TariffRepository
import za.co.statecapture.android.domain.model.TariffBlock
import za.co.statecapture.android.domain.model.TariffPeriod
import za.co.statecapture.android.domain.model.TariffProvider
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class CalculationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val repository = mockk<TariffRepository>(relaxed = true)
    private val purchaseDao = mockk<PurchaseDao>(relaxed = true)

    private val testMeter = Meter(
        id = 1,
        name = "Test Meter",
        meterNumber = "12345678901",
        providerId = "test_provider",
        icon = "⚡",
        isDefault = true,
        displayOrder = 0
    )

    // Tariff valid through 2027 so tests run regardless of the current year
    private val testProvider = TariffProvider(
        id = "test_provider",
        name = "Test Provider",
        type = "municipality",
        periods = listOf(
            TariffPeriod(
                validFrom = "2025-01-01",
                validTo = "2027-12-31",
                blocks = listOf(
                    TariffBlock(0, 100, 200.0),
                    TariffBlock(101, 999999, 300.0)
                ),
                fixedMonthlyChargeCents = 0
            )
        )
    )

    private lateinit var viewModel: CalculationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.getProvider("test_provider", any()) } returns testProvider
        coEvery { repository.getProvider("test_provider") } returns testProvider
        every { purchaseDao.getPurchasesForMeter(1) } returns flowOf(emptyList())
        coEvery { purchaseDao.getMonthlyTotalKwh(1, any()) } returns 0.0
        coEvery { purchaseDao.getMonthlyTotalKwhBetween(1, any(), any()) } returns 0.0
        viewModel = CalculationViewModel(repository, purchaseDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Quick-fill shortcut pill ──────────────────────────────────────────────

    @Test
    fun `onBlockShortcutClick formats needed kWh as clean integer string`() = runTest(testDispatcher) {
        viewModel.setMeter(testMeter)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onBlockShortcutClick(350.0) // target cumulative = 350; current = 0
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("350", viewModel.uiState.value.inputAmount)
        assertEquals(CalculationMode.KwhToRands, viewModel.uiState.value.mode)
    }

    @Test
    fun `onBlockShortcutClick subtracts existing cumulative kWh`() = runTest(testDispatcher) {
        // Simulate 50 kWh already purchased this month
        coEvery { purchaseDao.getMonthlyTotalKwh(1, any()) } returns 50.0
        every { purchaseDao.getPurchasesForMeter(1) } returns flowOf(
            listOf(Purchase(meterId = 1, amountCents = 10000.0, vatAmountCents = 0.0, kwhYield = 50.0, timestamp = System.currentTimeMillis()))
        )

        viewModel.setMeter(testMeter)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onBlockShortcutClick(100.0) // target = 100; current = 50; needed = 50
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("50", viewModel.uiState.value.inputAmount)
    }

    // ── VAT splitting ─────────────────────────────────────────────────────────

    @Test
    fun `VAT splitting for R100 inclusive is exact integer cents`() = runTest(testDispatcher) {
        viewModel.setMeter(testMeter)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onModeChange(CalculationMode.RandsToKwh)
        viewModel.onInputAmountChange("100")
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.uiState.value.result
        assertNotNull(result)
        // Total = 10000c; VAT = round(10000 * 15/115) = 1304c; excl = 8696c
        assertEquals(1304.0, result!!.vatAmountCents, 0.0)
        assertEquals(8696.0, result.result.totalCostCents, 0.0)
        // No rounding drift: excl + vat = exactly 10000
        assertEquals(10000.0, result.result.totalCostCents + result.vatAmountCents, 0.0)
    }

    @Test
    fun `VAT splitting for R1000 inclusive produces no drift`() = runTest(testDispatcher) {
        viewModel.setMeter(testMeter)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onModeChange(CalculationMode.RandsToKwh)
        viewModel.onInputAmountChange("1000")
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.uiState.value.result
        assertNotNull(result)
        // Total = 100000c; VAT = round(100000 * 15/115) = 13043c; excl = 86957c
        assertEquals(100000.0, result!!.result.totalCostCents + result.vatAmountCents, 0.0)
    }

    // ── Input filtering in onInputAmountChange ────────────────────────────────

    @Test
    fun `non-digit characters are stripped from input`() = runTest(testDispatcher) {
        viewModel.onInputAmountChange("R500")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("500", viewModel.uiState.value.inputAmount)
    }

    @Test
    fun `decimal point is stripped from input`() = runTest(testDispatcher) {
        viewModel.onInputAmountChange("100.5")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("1005", viewModel.uiState.value.inputAmount)
    }

    @Test
    fun `input above 100000 is rejected and state remains unchanged`() = runTest(testDispatcher) {
        viewModel.onInputAmountChange("500") // valid baseline
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputAmountChange("200000") // over the limit
        testDispatcher.scheduler.advanceUntilIdle()

        // State should remain "500" since 200000 was rejected
        assertEquals("500", viewModel.uiState.value.inputAmount)
    }

    @Test
    fun `input longer than 10 digits is rejected`() = runTest(testDispatcher) {
        viewModel.onInputAmountChange("500")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputAmountChange("12345678901") // 11 chars — over limit
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("500", viewModel.uiState.value.inputAmount)
    }



    // ── Sort toggle ───────────────────────────────────────────────────────────

    @Test
    fun `onSortChange - clicking same field toggles direction from DESC to ASC`() = runTest(testDispatcher) {
        viewModel.setMeter(testMeter)
        testDispatcher.scheduler.advanceUntilIdle()

        // Default is DATE DESC
        // First click on DATE → DATE ASC (toggled)
        viewModel.onSortChange(SortField.DATE)
        assertEquals(SortField.DATE, viewModel.uiState.value.sortField)
        assertEquals(SortDirection.ASC, viewModel.uiState.value.sortDirection)

        // Second click on DATE → DATE DESC (toggled again)
        viewModel.onSortChange(SortField.DATE)
        assertEquals(SortField.DATE, viewModel.uiState.value.sortField)
        assertEquals(SortDirection.DESC, viewModel.uiState.value.sortDirection)
    }

    @Test
    fun `onSortChange - switching to a different field resets direction to DESC`() = runTest(testDispatcher) {
        viewModel.setMeter(testMeter)
        testDispatcher.scheduler.advanceUntilIdle()

        // Default is DATE DESC. Click to establish DATE ASC
        viewModel.onSortChange(SortField.DATE)
        assertEquals(SortDirection.ASC, viewModel.uiState.value.sortDirection)

        // Switch to AMOUNT → should reset to DESC
        viewModel.onSortChange(SortField.AMOUNT)
        assertEquals(SortField.AMOUNT, viewModel.uiState.value.sortField)
        assertEquals(SortDirection.DESC, viewModel.uiState.value.sortDirection)
    }

    // ── Month navigation boundary ─────────────────────────────────────────────

    @Test
    fun `goToPreviousMonth decrements the selected month`() = runTest(testDispatcher) {
        viewModel.setMeter(testMeter)
        testDispatcher.scheduler.advanceUntilIdle()

        val before = viewModel.uiState.value.selectedYearMonth
        viewModel.goToPreviousMonth()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(before.minusMonths(1), viewModel.uiState.value.selectedYearMonth)
    }

    @Test
    fun `goToNextMonth does not navigate past the current month`() = runTest(testDispatcher) {
        viewModel.setMeter(testMeter)
        testDispatcher.scheduler.advanceUntilIdle()

        // Already at current month — goToNextMonth should be a no-op
        val current = viewModel.uiState.value.selectedYearMonth
        assertEquals(YearMonth.now(), current)

        viewModel.goToNextMonth()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(YearMonth.now(), viewModel.uiState.value.selectedYearMonth)
    }

    @Test
    fun `goToNextMonth advances from a past month`() = runTest(testDispatcher) {
        viewModel.setMeter(testMeter)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.goToPreviousMonth()
        testDispatcher.scheduler.advanceUntilIdle()

        val pastMonth = viewModel.uiState.value.selectedYearMonth
        viewModel.goToNextMonth()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(pastMonth.plusMonths(1), viewModel.uiState.value.selectedYearMonth)
    }
}
