package za.co.statecapture.android.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.statecapture.android.domain.model.TariffBlock
import za.co.statecapture.android.domain.model.TariffPeriod
import za.co.statecapture.android.domain.model.TariffProvider
import java.time.LocalDate

class TariffCalculatorTest {

    private val calculator = TariffCalculator()

    // Two-block tiered provider (Block 1: 0-100 kWh @ 200c; Block 2: 101+ @ 300c)
    // 2026 period adds a flat-rate with R10 fixed monthly charge.
    private val testProvider = TariffProvider(
        id = "test_provider",
        name = "Test Provider",
        type = "municipality",
        periods = listOf(
            TariffPeriod(
                validFrom = "2025-01-01",
                validTo = "2025-12-31",
                blocks = listOf(
                    TariffBlock(0, 100, 200.0),       // Block 1: 0-100 kWh @ 200c
                    TariffBlock(101, 999999, 300.0)    // Block 2: 101+ kWh @ 300c
                ),
                fixedMonthlyChargeCents = 0
            ),
            TariffPeriod(
                validFrom = "2026-01-01",
                validTo = "2027-12-31",
                blocks = listOf(
                    TariffBlock(0, 999999, 250.0)      // Flat rate @ 250c; R10 fixed charge
                ),
                fixedMonthlyChargeCents = 1000
            )
        )
    )

    // Provider with a hard cap at 300 kWh (last block's maxKwh is not 999999)
    private val cappedProvider = TariffProvider(
        id = "capped_provider",
        name = "Capped Provider",
        type = "municipality",
        periods = listOf(
            TariffPeriod(
                validFrom = "2025-01-01",
                validTo = "2027-12-31",
                blocks = listOf(
                    TariffBlock(0, 100, 200.0),
                    TariffBlock(101, 300, 300.0)       // Hard cap at 300 kWh
                ),
                fixedMonthlyChargeCents = 0
            )
        )
    )

    // ── calculateYield ────────────────────────────────────────────────────────

    @Test
    fun `calculate 2025 yield - first purchase`() {
        val date = LocalDate.of(2025, 6, 1)
        val result = calculator.calculateYield(testProvider, 20000.0, 0.0, date) // R200

        // R200 @ 200c = 100 kWh (exactly fills Block 1)
        assertEquals(100.0, result.totalKwh, 0.001)
        assertEquals(1, result.blockBreakdown.size)
        assertEquals(20000.0, result.blockBreakdown[0].costCents, 0.001)
    }

    @Test
    fun `calculate 2025 yield - spill over into second block`() {
        val date = LocalDate.of(2025, 6, 1)
        val result = calculator.calculateYield(testProvider, 50000.0, 0.0, date) // R500

        // Block 1: 100 kWh @ R2.00 = R200
        // Block 2: R300 / 300c = 100 kWh
        // Total = 200 kWh
        assertEquals(200.0, result.totalKwh, 0.001)
        assertEquals(2, result.blockBreakdown.size)
        assertEquals(100.0, result.blockBreakdown[0].kwhYield, 0.001)
        assertEquals(100.0, result.blockBreakdown[1].kwhYield, 0.001)
    }

    @Test
    fun `calculate 2026 yield - flat rate with fixed charge`() {
        val date = LocalDate.of(2026, 1, 1)
        val result = calculator.calculateYield(testProvider, 25000.0, 0.0, date) // R250

        // R250 - R10 fixed charge = R240 @ 250c = 96 kWh
        assertEquals(96.0, result.totalKwh, 0.001)
        assertEquals(1000, result.fixedChargeCents)
    }

    @Test
    fun `calculate yield - future date with no tariff`() {
        val date = LocalDate.of(2028, 1, 1)
        val result = calculator.calculateYield(testProvider, 10000.0, 0.0, date)

        assertEquals(0.0, result.totalKwh, 0.001)
        assertEquals("The tariffs for this period (2028) have not been published yet.", result.errorMessage)
    }

    // ── calculateCost ─────────────────────────────────────────────────────────

    @Test
    fun `calculate cost - basic within single block`() {
        val date = LocalDate.of(2025, 6, 1)
        // 50 kWh, all within Block 1 (@ 200c)
        val result = calculator.calculateCost(testProvider, 50.0, 0.0, date)

        assertEquals(10000.0, result.totalCostCents, 0.001) // 50 x 200c = R100
        assertEquals(50.0, result.totalKwh, 0.001)
        assertEquals(1, result.blockBreakdown.size)
        assertNull(result.errorMessage)
    }

    @Test
    fun `calculate cost - spillover across two blocks`() {
        val date = LocalDate.of(2025, 6, 1)
        // 150 kWh: first 100 kWh @ 200c, then 50 kWh @ 300c
        val result = calculator.calculateCost(testProvider, 150.0, 0.0, date)

        // Block 1: 100 x 200c = 20000c; Block 2: 50 x 300c = 15000c; Total = 35000c
        assertEquals(35000.0, result.totalCostCents, 0.001)
        assertEquals(150.0, result.totalKwh, 0.001)
        assertEquals(2, result.blockBreakdown.size)
        assertEquals(100.0, result.blockBreakdown[0].kwhYield, 0.001)
        assertEquals(50.0, result.blockBreakdown[1].kwhYield, 0.001)
    }

    @Test
    fun `calculate cost - with previous purchases already in block 2`() {
        val date = LocalDate.of(2025, 6, 1)
        // 100 kWh already purchased (Block 1 fully consumed).
        // Now buying 50 more kWh: all at Block 2 rate (300c).
        val result = calculator.calculateCost(testProvider, 50.0, 100.0, date)

        assertEquals(15000.0, result.totalCostCents, 0.001) // 50 x 300c = R150
        assertEquals(50.0, result.totalKwh, 0.001)
        assertEquals(1, result.blockBreakdown.size)
        assertEquals(300.0, result.blockBreakdown[0].ratePerKwhCents, 0.001)
    }

    @Test
    fun `calculate cost - first purchase includes fixed monthly charge`() {
        val date = LocalDate.of(2026, 6, 1)
        // 100 kWh @ 250c = 25000c; plus R10 fixed charge (1000c) on first purchase
        val result = calculator.calculateCost(testProvider, 100.0, 0.0, date, includeFixedCharge = true)

        assertEquals(26000.0, result.totalCostCents, 0.001) // R250 + R10 = R260
        assertEquals(1000, result.fixedChargeCents)
    }

    @Test
    fun `calculate cost - fixed charge skipped when already paid this month`() {
        val date = LocalDate.of(2026, 6, 1)
        // Second purchase in the month: fixed charge must NOT be added
        val result = calculator.calculateCost(testProvider, 100.0, 50.0, date, includeFixedCharge = false)

        assertEquals(25000.0, result.totalCostCents, 0.001) // 100 x 250c only
    }

    @Test
    fun `calculate cost - no tariff for future date returns zero cost`() {
        val date = LocalDate.of(2028, 1, 1)
        val result = calculator.calculateCost(testProvider, 100.0, 0.0, date)

        assertEquals(0.0, result.totalCostCents, 0.001)
        assertEquals(0.0, result.totalKwh, 0.001)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `calculate cost - expired tariff period returns zero cost and zero kwh`() {
        val expiredProvider = TariffProvider(
            id = "expired_provider",
            name = "Expired Provider",
            type = "municipality",
            periods = listOf(
                TariffPeriod(
                    validFrom = "2024-07-01",
                    validTo = "2025-06-30",
                    blocks = listOf(TariffBlock(0, 999999, 300.0)),
                    fixedMonthlyChargeCents = 1000
                )
            )
        )
        val date = LocalDate.of(2026, 9, 7)
        val result = calculator.calculateCost(expiredProvider, 100.0, 0.0, date)

        assertEquals(0.0, result.totalCostCents, 0.001)
        assertEquals(0.0, result.totalKwh, 0.001)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `calculate cost - capped provider marks result as capped and limits kWh`() {
        val date = LocalDate.of(2025, 6, 1)
        // Request 400 kWh but the provider caps at 300 kWh
        val result = calculator.calculateCost(cappedProvider, 400.0, 0.0, date)

        assertTrue(result.isCapped)
        assertEquals(300.0, result.totalKwh, 0.001)  // only 300 kWh fit
        assertEquals(300.0, result.maxAvailableKwh!!, 0.001)
    }

    // ── calculateCumulativeBreakdown ──────────────────────────────────────────

    @Test
    fun `calculate cumulative breakdown - all units within Block 1`() {
        val date = LocalDate.of(2025, 6, 1)
        val breakdown = calculator.calculateCumulativeBreakdown(testProvider, 60.0, date)

        assertEquals(1, breakdown.size)
        assertEquals(60.0, breakdown[0].kwhYield, 0.001)
        assertEquals(200.0, breakdown[0].ratePerKwhCents, 0.001)
        assertEquals(12000.0, breakdown[0].costCents, 0.001) // 60 x 200c
    }

    @Test
    fun `calculate cumulative breakdown - units cross two blocks`() {
        val date = LocalDate.of(2025, 6, 1)
        // 150 kWh: 100 in Block 1, 50 in Block 2
        val breakdown = calculator.calculateCumulativeBreakdown(testProvider, 150.0, date)

        assertEquals(2, breakdown.size)
        assertEquals(100.0, breakdown[0].kwhYield, 0.001)
        assertEquals(50.0, breakdown[1].kwhYield, 0.001)
        assertEquals(200.0, breakdown[0].ratePerKwhCents, 0.001)
        assertEquals(300.0, breakdown[1].ratePerKwhCents, 0.001)
    }

    @Test
    fun `calculate cumulative breakdown - zero kWh returns empty list`() {
        val date = LocalDate.of(2025, 6, 1)
        val breakdown = calculator.calculateCumulativeBreakdown(testProvider, 0.0, date)

        assertTrue(breakdown.isEmpty())
    }

    @Test
    fun `calculate cumulative breakdown - no tariff for date returns empty list`() {
        val date = LocalDate.of(2028, 1, 1)
        val breakdown = calculator.calculateCumulativeBreakdown(testProvider, 100.0, date)

        assertTrue(breakdown.isEmpty())
    }

    // ── City of Tshwane 2026/2027 Residential Prepaid Verification ───────────

    private val tshwane2026Provider = TariffProvider(
        id = "tshwane_prepaid",
        name = "City of Tshwane - Residential Prepaid",
        type = "municipality",
        periods = listOf(
            TariffPeriod(
                validFrom = "2026-07-01",
                validTo = "2027-06-30",
                blocks = listOf(
                    TariffBlock(0, 100, 324.12),
                    TariffBlock(101, 400, 379.32),
                    TariffBlock(401, 650, 413.26),
                    TariffBlock(651, 999999, 445.51)
                ),
                fixedMonthlyChargeCents = 0
            )
        )
    )

    @Test
    fun `tshwane 2026 prepaid - first purchase of R500 incl VAT yields 129_2 kWh`() {
        val date = LocalDate.of(2026, 10, 11)
        // R500 incl VAT: totalCents = 50000, vat = round(50000 * 15 / 115) = 6522, exclCents = 43478
        val exclCents = 50000.0 - Math.round(50000.0 * 15.0 / 115.0)
        assertEquals(43478.0, exclCents, 0.001)

        val result = calculator.calculateYield(tshwane2026Provider, exclCents, 0.0, date)

        // Block 1: 100 kWh @ 324.12c = 32412.0c
        // Remaining: 43478.0 - 32412.0 = 11066.0c
        // Block 2: 11066.0c / 379.32c = 29.173 kWh
        // Total: 129.173 kWh -> 129.2 kWh
        assertEquals(129.17, result.totalKwh, 0.05)
        assertEquals(String.format(java.util.Locale.US, "%.1f", 129.17), String.format(java.util.Locale.US, "%.1f", result.totalKwh))
    }

    @Test
    fun `tshwane 2026 prepaid - prior purchase of R235 incl VAT yields 63_0 kWh`() {
        val date = LocalDate.of(2026, 10, 10)
        // R235 incl VAT: totalCents = 23500, vat = round(23500 * 15 / 115) = 3065, exclCents = 20435
        val exclCents = 23500.0 - Math.round(23500.0 * 15.0 / 115.0)
        assertEquals(20435.0, exclCents, 0.001)

        val result = calculator.calculateYield(tshwane2026Provider, exclCents, 0.0, date)

        // Block 1: 20435.0c / 324.12c = 63.0476 kWh -> 63.0 kWh
        assertEquals(63.05, result.totalKwh, 0.05)
        assertEquals("63.0", String.format(java.util.Locale.US, "%.1f", result.totalKwh))
    }

    @Test
    fun `tshwane 2026 prepaid - second purchase of R500 incl VAT after 63 kWh yields exactly 120_0 kWh`() {
        val date = LocalDate.of(2026, 10, 11)
        val exclCents = 50000.0 - Math.round(50000.0 * 15.0 / 115.0) // 43478 cents

        // 63.0 kWh already purchased earlier in the month
        val result = calculator.calculateYield(tshwane2026Provider, exclCents, 63.0, date)

        // Remaining in Block 1: 100 - 63 = 37.0 kWh @ 324.12c = 11992.44c
        // Remaining funds: 43478 - 11992.44 = 31485.56c
        // Block 2: 31485.56c / 379.32c = 83.005 kWh
        // Total: 37.0 + 83.005 = 120.005 kWh -> 120.0 kWh (matches FNB token exactly!)
        assertEquals(120.0, result.totalKwh, 0.05)
        assertEquals("120.0", String.format(java.util.Locale.US, "%.1f", result.totalKwh))
    }
}

