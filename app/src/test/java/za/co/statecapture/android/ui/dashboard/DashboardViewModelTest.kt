package za.co.statecapture.android.ui.dashboard

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import za.co.statecapture.android.data.Meter
import za.co.statecapture.android.data.MeterDao
import za.co.statecapture.android.data.Purchase
import za.co.statecapture.android.data.PurchaseDao

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val purchaseDao = mockk<PurchaseDao>(relaxed = true)
    private val meterDao = mockk<MeterDao>(relaxed = true)

    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Default flow mock returns to prevent crashes
        every { purchaseDao.getTotalCentsSince(any()) } returns flowOf(0.0)
        every { purchaseDao.getTotalVatCentsSince(any()) } returns flowOf(0.0)
        every { purchaseDao.getTotalKwhSince(any()) } returns flowOf(0.0)
        every { purchaseDao.getAllTimeTotalCents() } returns flowOf(0.0)
        every { purchaseDao.getAllTimeTotalVatCents() } returns flowOf(0.0)
        every { purchaseDao.getAllTimeTotalKwh() } returns flowOf(0.0)
        every { purchaseDao.getAllPurchases() } returns flowOf(emptyList())
        every { purchaseDao.getDistinctMonthCount() } returns flowOf(0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onboarding welcome state reflects having no meters and no purchases`() = runTest(testDispatcher) {
        // Given meterDao and purchaseDao emit empty list
        every { meterDao.getAllMeters() } returns flowOf(emptyList())
        every { purchaseDao.getAllPurchases() } returns flowOf(emptyList())

        viewModel = DashboardViewModel(purchaseDao, meterDao)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasMeters)
        assertFalse(state.hasPurchases)
    }

    @Test
    fun `onboarding welcome state reflects having meters but no purchases`() = runTest(testDispatcher) {
        // Given meterDao emits a meter but purchaseDao remains empty
        val mockMeter = Meter(
            id = 1,
            name = "Home",
            meterNumber = "12345678901",
            providerId = "test",
            icon = "⚡"
        )
        every { meterDao.getAllMeters() } returns flowOf(listOf(mockMeter))
        every { purchaseDao.getAllPurchases() } returns flowOf(emptyList())

        viewModel = DashboardViewModel(purchaseDao, meterDao)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasMeters)
        assertFalse(state.hasPurchases)
    }

    @Test
    fun `onboarding welcome state disappears once purchases exist`() = runTest(testDispatcher) {
        val mockMeter = Meter(
            id = 1,
            name = "Home",
            meterNumber = "12345678901",
            providerId = "test",
            icon = "⚡"
        )
        val mockPurchase = Purchase(
            id = 1,
            meterId = 1,
            amountCents = 10000.0,
            vatAmountCents = 1500.0,
            kwhYield = 50.0,
            timestamp = System.currentTimeMillis()
        )
        every { meterDao.getAllMeters() } returns flowOf(listOf(mockMeter))
        every { purchaseDao.getAllPurchases() } returns flowOf(listOf(mockPurchase))

        viewModel = DashboardViewModel(purchaseDao, meterDao)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasMeters)
        assertTrue(state.hasPurchases)
    }
}
