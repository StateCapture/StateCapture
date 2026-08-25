package za.co.statecapture.android.ui.calculator

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import za.co.statecapture.android.data.Meter
import za.co.statecapture.android.data.Purchase
import za.co.statecapture.android.data.PurchaseDao
import za.co.statecapture.android.data.repository.TariffRepository
import za.co.statecapture.android.domain.model.TariffBlock
import za.co.statecapture.android.domain.model.TariffPeriod
import za.co.statecapture.android.domain.model.TariffProvider
import java.time.LocalDate

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

    private val testProvider = TariffProvider(
        id = "test_provider",
        name = "Test Provider",
        type = "municipality",
        periods = listOf(
            TariffPeriod(
                validFrom = "2025-01-01",
                validTo = "2027-12-31",
                blocks = listOf(
                    TariffBlock(0, 100, 200.0), // Block 1: 0-100 kWh @ 200c
                    TariffBlock(101, 300, 300.0) // Block 2: 101-300 kWh @ 300c
                ),
                fixedMonthlyChargeCents = 0
            )
        )
    )

    private lateinit var viewModel: CalculationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Default stubbing
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

    @Test
    fun `onBlockShortcutClick formats needed kwh to clean integer string`() = runTest(testDispatcher) {
        viewModel.setMeter(testMeter)
        testDispatcher.scheduler.advanceUntilIdle()

        // 350 units target cumulative kwh. Current is 0, so needed is 350.
        // It should update inputAmount to "350" (integer) instead of "350.0".
        viewModel.onBlockShortcutClick(350.0)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals("350", uiState.inputAmount)
        assertEquals(CalculationMode.KwhToRands, uiState.mode)
    }

    @Test
    fun `VAT splitting in RandsToKwh mode is exact with integer cents`() = runTest(testDispatcher) {
        viewModel.setMeter(testMeter)
        testDispatcher.scheduler.advanceUntilIdle()

        // Set mode to RandsToKwh and input 100.00 Rands
        viewModel.onModeChange(CalculationMode.RandsToKwh)
        viewModel.onInputAmountChange("100")
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        val result = uiState.result
        assertNotNull(result)

        // For R100.00 inclusive of 15% VAT:
        // Total cents = 10000
        // VAT amount = Math.round(10000 * 15.0 / 115.0) = 1304
        // Excl VAT amount = 10000 - 1304 = 8696
        assertEquals(1304.0, result!!.vatAmountCents, 0.0)
        assertEquals(8696.0, result.result.totalCostCents, 0.0)
        assertEquals(10000.0, result.result.totalCostCents + result.vatAmountCents, 0.0)
    }
}
