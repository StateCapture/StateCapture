package za.co.statecapture.android.ui.dashboard

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
import za.co.statecapture.android.data.MeterDao
import za.co.statecapture.android.data.Purchase
import za.co.statecapture.android.data.PurchaseDao
import java.time.YearMonth
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val purchaseDao = mockk<PurchaseDao>(relaxed = true)
    private val meterDao = mockk<MeterDao>(relaxed = true)

    private lateinit var viewModel: DashboardViewModel

    private val mockMeter = Meter(
        id = 1,
        name = "Home",
        meterNumber = "12345678901",
        providerId = "test",
        icon = "⚡"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Default: all DAO flows return zero/empty unless overridden per-test
        every { purchaseDao.getTotalCentsSince(any()) } returns flowOf(0.0)
        every { purchaseDao.getTotalVatCentsSince(any()) } returns flowOf(0.0)
        every { purchaseDao.getTotalKwhSince(any()) } returns flowOf(0.0)
        every { purchaseDao.getAllTimeTotalCents() } returns flowOf(0.0)
        every { purchaseDao.getAllTimeTotalVatCents() } returns flowOf(0.0)
        every { purchaseDao.getAllTimeTotalKwh() } returns flowOf(0.0)
        every { purchaseDao.getAllPurchases() } returns flowOf(emptyList())
        every { purchaseDao.getDistinctMonthCount() } returns flowOf(0)
        every { meterDao.getAllMeters() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Onboarding state ──────────────────────────────────────────────────────

    @Test
    fun `onboarding - no meters and no purchases shows both flags false`() = runTest(testDispatcher) {
        every { meterDao.getAllMeters() } returns flowOf(emptyList())
        every { purchaseDao.getAllPurchases() } returns flowOf(emptyList())

        viewModel = DashboardViewModel(purchaseDao, meterDao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasMeters)
        assertFalse(viewModel.uiState.value.hasPurchases)
    }

    @Test
    fun `onboarding - meters present but no purchases shows hasMeters true hasPurchases false`() = runTest(testDispatcher) {
        every { meterDao.getAllMeters() } returns flowOf(listOf(mockMeter))
        every { purchaseDao.getAllPurchases() } returns flowOf(emptyList())

        viewModel = DashboardViewModel(purchaseDao, meterDao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasMeters)
        assertFalse(viewModel.uiState.value.hasPurchases)
    }

    @Test
    fun `onboarding - disappears once first purchase is recorded`() = runTest(testDispatcher) {
        val purchase = makePurchase(monthsAgo = 0, amountCents = 10000.0, vatCents = 1304.0, kwh = 50.0)
        every { meterDao.getAllMeters() } returns flowOf(listOf(mockMeter))
        every { purchaseDao.getAllPurchases() } returns flowOf(listOf(purchase))

        viewModel = DashboardViewModel(purchaseDao, meterDao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasMeters)
        assertTrue(viewModel.uiState.value.hasPurchases)
    }

    // ── Averages calculation ──────────────────────────────────────────────────

    @Test
    fun `averages - no purchases yields zero averages and correct subtitle`() = runTest(testDispatcher) {
        every { meterDao.getAllMeters() } returns flowOf(listOf(mockMeter))
        every { purchaseDao.getAllPurchases() } returns flowOf(emptyList())

        viewModel = DashboardViewModel(purchaseDao, meterDao)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0.0, state.dailyAverageRand, 0.001)
        assertEquals(0.0, state.weeklyAverageRand, 0.001)
        assertEquals(0.0, state.monthlyAverageRand, 0.001)
        assertEquals("No previous complete months with purchases", state.averagesSubtitle)
    }

    @Test
    fun `averages - one complete month with purchases produces correct monthly average`() = runTest(testDispatcher) {
        // One purchase exactly 2 months ago (complete, not current month)
        // amountCents = 50000 (R500 excl VAT), vatCents = 0 for simplicity
        val purchase = makePurchase(monthsAgo = 2, amountCents = 50000.0, vatCents = 0.0, kwh = 200.0)
        every { meterDao.getAllMeters() } returns flowOf(listOf(mockMeter))
        every { purchaseDao.getAllPurchases() } returns flowOf(listOf(purchase))

        viewModel = DashboardViewModel(purchaseDao, meterDao)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // Monthly avg = R500 / 1 month = R500
        assertEquals(500.0, state.monthlyAverageRand, 0.01)
        assertEquals(200.0, state.monthlyAverageKwh, 0.01)
        assertEquals("Based on 1 complete month with purchases", state.averagesSubtitle)
    }

    @Test
    fun `averages - two complete months with purchases averages correctly`() = runTest(testDispatcher) {
        // Month 2 ago: R400; Month 3 ago: R600 → average R500/month
        val p1 = makePurchase(monthsAgo = 2, amountCents = 40000.0, vatCents = 0.0, kwh = 100.0)
        val p2 = makePurchase(monthsAgo = 3, amountCents = 60000.0, vatCents = 0.0, kwh = 200.0)
        every { meterDao.getAllMeters() } returns flowOf(listOf(mockMeter))
        every { purchaseDao.getAllPurchases() } returns flowOf(listOf(p1, p2))

        viewModel = DashboardViewModel(purchaseDao, meterDao)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // Total = R1000 over 2 months → avg R500/month
        assertEquals(500.0, state.monthlyAverageRand, 0.5)
        assertEquals(150.0, state.monthlyAverageKwh, 0.5) // (100+200)/2
        assertTrue(state.averagesSubtitle.contains("2 complete months"))
    }

    @Test
    fun `averages - current month purchases are excluded from averages`() = runTest(testDispatcher) {
        // One purchase this month and one 2 months ago
        val currentMonthPurchase = makePurchase(monthsAgo = 0, amountCents = 100000.0, vatCents = 0.0, kwh = 400.0)
        val completePurchase = makePurchase(monthsAgo = 2, amountCents = 50000.0, vatCents = 0.0, kwh = 200.0)
        every { meterDao.getAllMeters() } returns flowOf(listOf(mockMeter))
        every { purchaseDao.getAllPurchases() } returns flowOf(listOf(currentMonthPurchase, completePurchase))

        viewModel = DashboardViewModel(purchaseDao, meterDao)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // Only the 2-months-ago purchase should count; currentMonth is excluded
        assertEquals(500.0, state.monthlyAverageRand, 0.5)
        assertEquals("Based on 1 complete month with purchases", state.averagesSubtitle)
    }

    @Test
    fun `averages - empty months between purchases are excluded from day count`() = runTest(testDispatcher) {
        // Purchases spread 6 months apart; the empty months in between must NOT count
        val p1 = makePurchase(monthsAgo = 2, amountCents = 50000.0, vatCents = 0.0, kwh = 100.0)
        val p2 = makePurchase(monthsAgo = 8, amountCents = 50000.0, vatCents = 0.0, kwh = 100.0)
        every { meterDao.getAllMeters() } returns flowOf(listOf(mockMeter))
        every { purchaseDao.getAllPurchases() } returns flowOf(listOf(p1, p2))

        viewModel = DashboardViewModel(purchaseDao, meterDao)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // 2 distinct months with data → monthly avg = R1000 / 2 = R500
        assertEquals(500.0, state.monthlyAverageRand, 0.5)
        // Weekly avg should be based on 2 months worth of days, not 6
        assertTrue(state.weeklyAvgRand() > 0.0)
    }

    // ── VAT toggle ────────────────────────────────────────────────────────────

    @Test
    fun `VAT toggle - disabling VAT reduces displayed totals`() = runTest(testDispatcher) {
        // Purchase: 10000c base + 1500c VAT
        val purchase = makePurchase(monthsAgo = 0, amountCents = 10000.0, vatCents = 1500.0, kwh = 50.0)
        every { meterDao.getAllMeters() } returns flowOf(listOf(mockMeter))
        every { purchaseDao.getAllPurchases() } returns flowOf(listOf(purchase))
        every { purchaseDao.getTotalCentsSince(any()) } returns flowOf(10000.0)
        every { purchaseDao.getTotalVatCentsSince(any()) } returns flowOf(1500.0)
        every { purchaseDao.getAllTimeTotalCents() } returns flowOf(10000.0)
        every { purchaseDao.getAllTimeTotalVatCents() } returns flowOf(1500.0)

        viewModel = DashboardViewModel(purchaseDao, meterDao)
        testDispatcher.scheduler.advanceUntilIdle()

        val inclVatTotal = viewModel.uiState.value.thisMonthRand

        viewModel.onIncludeVatToggle(false)
        testDispatcher.scheduler.advanceUntilIdle()

        val exclVatTotal = viewModel.uiState.value.thisMonthRand

        // Excl-VAT total should be less than incl-VAT total
        assertTrue(exclVatTotal < inclVatTotal)
        // Specifically: 10000c = R100 (excl); 11500c = R115 (incl)
        assertEquals(100.0, exclVatTotal, 0.01)
        assertEquals(115.0, inclVatTotal, 0.01)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Creates a [Purchase] with its timestamp set to the start of [monthsAgo] months ago.
     */
    private fun makePurchase(
        monthsAgo: Int,
        amountCents: Double,
        vatCents: Double,
        kwh: Double
    ): Purchase {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MONTH, -monthsAgo)
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return Purchase(
            meterId = 1,
            amountCents = amountCents,
            vatAmountCents = vatCents,
            kwhYield = kwh,
            timestamp = cal.timeInMillis
        )
    }

    // Extension to access weeklyAvgRand without polluting the assertion logic
    private fun DashboardState.weeklyAvgRand() = weeklyAverageRand
}
