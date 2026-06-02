package za.co.statecapture.android.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import za.co.statecapture.android.domain.model.TariffBlock
import za.co.statecapture.android.domain.model.TariffPeriod
import za.co.statecapture.android.domain.model.TariffProvider
import java.time.LocalDate

class TariffCalculatorTest {

    private val calculator = TariffCalculator()

    private val testProvider = TariffProvider(
        id = "test_provider",
        name = "Test Provider",
        type = "municipality",
        periods = listOf(
            TariffPeriod(
                validFrom = "2025-01-01",
                validTo = "2025-12-31",
                blocks = listOf(
                    TariffBlock(0, 100, 200.0), // Block 1: 0-100kWh @ 200c
                    TariffBlock(101, 999999, 300.0) // Block 2: 101+ @ 300c
                ),
                fixedMonthlyChargeCents = 0
            ),
            TariffPeriod(
                validFrom = "2026-01-01",
                validTo = "2026-12-31",
                blocks = listOf(
                    TariffBlock(0, 999999, 250.0) // 2026 is Flat Rate @ 250c
                ),
                fixedMonthlyChargeCents = 1000
            )
        )
    )

    @Test
    fun `calculate 2025 yield - first purchase`() {
        val date = LocalDate.of(2025, 6, 1)
        val result = calculator.calculateYield(testProvider, 20000.0, 0.0, date) // R200
        
        // R200 @ 200c = 100kWh (exactly the first block)
        assertEquals(100.0, result.totalKwh, 0.001)
        assertEquals(1, result.blockBreakdown.size)
        assertEquals(20000.0, result.blockBreakdown[0].costCents, 0.001)
    }

    @Test
    fun `calculate 2025 yield - spill over into second block`() {
        val date = LocalDate.of(2025, 6, 1)
        val result = calculator.calculateYield(testProvider, 50000.0, 0.0, date) // R500
        
        // R500:
        // First 100kWh costs R200 (20000c)
        // Remaining R300 (30000c) / 300c = 100kWh
        // Total = 200kWh
        assertEquals(200.0, result.totalKwh, 0.001)
        assertEquals(2, result.blockBreakdown.size)
        assertEquals(100.0, result.blockBreakdown[0].kwhYield, 0.001)
        assertEquals(100.0, result.blockBreakdown[1].kwhYield, 0.001)
    }

    @Test
    fun `calculate 2026 yield - flat rate with fixed charge`() {
        val date = LocalDate.of(2026, 1, 1)
        val result = calculator.calculateYield(testProvider, 25000.0, 0.0, date) // R250
        
        // R250 - R10 fixed charge = R240. R240 @ 250c = 96kWh
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
}
