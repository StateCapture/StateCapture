package za.co.statecapture.android.ui.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class VatCalculationTest {

    @Test
    fun `calculate base input from inclusive rand input`() {
        val input = 115.0 // R115 inclusive
        val baseInput = Math.round(input * 100.0 / 1.15) / 100.0
        
        // R115 / 1.15 = R100.00
        assertEquals(100.0, baseInput, 0.001)
    }

    @Test
    fun `calculate base input from inclusive rand input - with cents`() {
        val input = 100.0 // R100 inclusive
        val baseInput = Math.round(input * 100.0 / 1.15) / 100.0
        
        // R100 / 1.15 = R86.9565... rounds to R86.96
        assertEquals(86.96, baseInput, 0.001)
    }

    @Test
    fun `calculate vat from base cost in cents`() {
        val baseCostCents = 10000.0 // R100.00 base cost
        val vatAmountCents = Math.round(baseCostCents * 0.15).toDouble()
        
        // 15% of 10000 = 1500
        assertEquals(1500.0, vatAmountCents, 0.001)
    }

    @Test
    fun `calculate vat from base cost in cents - rounding case`() {
        val baseCostCents = 10025.0 // R100.25 base cost
        val vatAmountCents = Math.round(baseCostCents * 0.15).toDouble()
        
        // 15% of 10025 = 1503.75 -> rounds to 1504
        assertEquals(1504.0, vatAmountCents, 0.001)
    }

    @Test
    fun `total display cost summation`() {
        val baseCostCents = 8696.0
        val vatAmountCents = 1304.0
        val totalCents = baseCostCents + vatAmountCents
        
        assertEquals(10000.0, totalCents, 0.001)
    }
}
