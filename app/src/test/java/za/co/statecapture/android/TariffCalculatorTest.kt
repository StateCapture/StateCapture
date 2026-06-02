import za.co.statecapture.android.domain.engine.TariffCalculator
import za.co.statecapture.android.domain.model.*

fun main() {
    val calculator = TariffCalculator()
    val provider = TariffProvider(
        id = "tshwane_residential",
        name = "City of Tshwane - Residential",
        type = "municipality",
        periods = listOf(
            TariffPeriod(
                validFrom = "2025-07-01",
                validTo = "2026-06-30",
                blocks = listOf(
                    TariffBlock(0, 100, 297.36),
                    TariffBlock(101, 400, 348.00),
                    TariffBlock(401, 650, 379.14),
                    TariffBlock(651, 999999, 408.73)
                ),
                fixedMonthlyChargeCents = 0
            )
        )
    )
    
    val result = calculator.calculateYield(provider, 1000000.0, 0.0)
    println("Total kWh: ${result.totalKwh}")
    result.blockBreakdown.forEach { 
        println("Block ${it.blockIndex}: ${it.kwhYield} kWh @ R${it.costCents/100.0}")
    }
}
