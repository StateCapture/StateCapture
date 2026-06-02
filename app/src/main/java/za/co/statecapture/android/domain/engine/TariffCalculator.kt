package za.co.statecapture.android.domain.engine

import za.co.statecapture.android.domain.model.TariffProvider
import za.co.statecapture.android.domain.model.TariffPeriod
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class BlockYield(
    val blockIndex: Int,
    val kwhYield: Double,
    val costCents: Double,
    val ratePerKwhCents: Double
)

class TariffCalculator {

    private fun parseDate(dateStr: String): LocalDate {
        return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private fun findActivePeriod(provider: TariffProvider, date: LocalDate): TariffPeriod? {
        for (period in provider.periods) {
            val validFrom = parseDate(period.validFrom)
            val validTo = period.validTo?.let { parseDate(it) }

            if (!date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo))) {
                return period
            }
        }
        return null
    }

    /**
     * Calculates the kWh yield for a given purchase amount, taking into account
     * previous purchases in the current month to determine the starting block.
     *
     * @param provider The provider's tariff structure.
     * @param purchaseAmountCents The amount of money being spent now (in cents).
     * @param previousPurchasesKwh The total kWh already purchased this month.
     * @param date The date of the purchase (defaults to today).
     * @return CalculationResult containing total yield and breakdown.
     */
    fun calculateYield(
        provider: TariffProvider,
        purchaseAmountCents: Double,
        previousPurchasesKwh: Double,
        date: LocalDate = LocalDate.now()
    ): CalculationResult {
        val activePeriod = findActivePeriod(provider, date)
        if (activePeriod == null) {
            val latestPeriodDate = provider.periods.mapNotNull { period ->
                period.validTo?.let { parseDate(it) } ?: parseDate(period.validFrom)
            }.maxOrNull()

            val message = if (latestPeriodDate != null && date.isAfter(latestPeriodDate)) {
                "The tariffs for this period (${date.year}) have not been published yet."
            } else {
                "No active tariff period found for date $date."
            }
            return CalculationResult(0.0, emptyList(), 0, totalCostCents = 0.0, errorMessage = message)
        }

        var remainingCents = purchaseAmountCents
        val fixedCharge = activePeriod.fixedMonthlyChargeCents
        
        // Fixed charge is usually deducted on the first purchase of the month
        if (previousPurchasesKwh <= 0.0 && fixedCharge > 0) {
            if (remainingCents < fixedCharge) {
                return CalculationResult(
                    totalKwh = 0.0,
                    blockBreakdown = emptyList(),
                    fixedChargeCents = fixedCharge,
                    totalCostCents = purchaseAmountCents,
                    errorMessage = "Amount is less than the monthly fixed charge (R${fixedCharge/100.0})"
                )
            }
            remainingCents -= fixedCharge
        }

        var currentKwhAccumulator = previousPurchasesKwh
        val breakdown = mutableListOf<BlockYield>()
        var totalYield = 0.0

        for ((index, block) in activePeriod.blocks.withIndex()) {
            if (remainingCents <= 1e-6 && block.ratePerKwhCents > 0) break

            // Skip blocks that are already completely filled by previous purchases
            if (currentKwhAccumulator >= block.maxKwh) {
                continue
            }

            // How much room is left in this block?
            val kwhAvailableInBlock = block.maxKwh - currentKwhAccumulator
            val maxCostForBlock = kwhAvailableInBlock * block.ratePerKwhCents

            if (block.maxKwh < 999999 && remainingCents >= maxCostForBlock) {
                // This block is fully consumed
                if (kwhAvailableInBlock > 0) {
                    breakdown.add(
                        BlockYield(
                            blockIndex = index + 1,
                            kwhYield = kwhAvailableInBlock,
                            costCents = maxCostForBlock,
                            ratePerKwhCents = block.ratePerKwhCents
                        )
                    )
                    totalYield += kwhAvailableInBlock
                    currentKwhAccumulator += kwhAvailableInBlock
                    remainingCents -= maxCostForBlock
                }
            } else {
                // This block is only partially consumed (or it's the last block)
                val yieldInBlock = remainingCents / block.ratePerKwhCents
                if (yieldInBlock > 0) {
                    breakdown.add(
                        BlockYield(
                            blockIndex = index + 1,
                            kwhYield = yieldInBlock,
                            costCents = remainingCents,
                            ratePerKwhCents = block.ratePerKwhCents
                        )
                    )
                    totalYield += yieldInBlock
                    currentKwhAccumulator += yieldInBlock
                }
                remainingCents = 0.0
                break
            }
        }

        val actualCostSpent = purchaseAmountCents - remainingCents
        val lastBlock = activePeriod.blocks.lastOrNull()
        val absoluteMaxKwh = if (lastBlock != null && lastBlock.maxKwh < 999999) lastBlock.maxKwh.toDouble() else null
        val isCapped = absoluteMaxKwh != null && remainingCents > 1e-6

        return CalculationResult(
            totalKwh = totalYield,
            blockBreakdown = breakdown,
            fixedChargeCents = activePeriod.fixedMonthlyChargeCents,
            totalCostCents = actualCostSpent,
            isCapped = isCapped,
            maxAvailableKwh = absoluteMaxKwh,
            errorMessage = if (isCapped) "Capped at ${absoluteMaxKwh?.toInt()} units — this provider does not allow purchases beyond this limit." else null
        )
    }

    /**
     * Calculates the cost for a given kWh amount, taking into account
     * previous purchases in the current month to determine the starting block.
     *
     * @param provider The provider's tariff structure.
     * @param targetKwh The number of units (kWh) being purchased.
     * @param previousPurchasesKwh The total kWh already purchased this month.
     * @param date The date of the purchase.
     * @return CalculationResult containing total cost and breakdown.
     */
    fun calculateCost(
        provider: TariffProvider,
        targetKwh: Double,
        previousPurchasesKwh: Double,
        date: LocalDate = LocalDate.now()
    ): CalculationResult {
        val activePeriod = findActivePeriod(provider, date)
        if (activePeriod == null) {
            return CalculationResult(targetKwh, emptyList(), 0, totalCostCents = 0.0, errorMessage = "No active tariff period found.")
        }

        var remainingKwh = targetKwh
        var currentKwhAccumulator = previousPurchasesKwh
        
        val breakdown = mutableListOf<BlockYield>()
        var totalCostCents = activePeriod.fixedMonthlyChargeCents.toDouble()
        if (previousPurchasesKwh > 0) {
            totalCostCents = 0.0 // Only apply on first purchase
        }

        for ((index, block) in activePeriod.blocks.withIndex()) {
            if (remainingKwh <= 1e-6) break

            if (currentKwhAccumulator >= block.maxKwh) {
                continue
            }

            val kwhAvailableInBlock = block.maxKwh - currentKwhAccumulator
            
            if (block.maxKwh < 999999 && remainingKwh >= kwhAvailableInBlock) {
                if (kwhAvailableInBlock > 0) {
                    val costInBlock = kwhAvailableInBlock * block.ratePerKwhCents
                    breakdown.add(
                        BlockYield(
                            blockIndex = index + 1,
                            kwhYield = kwhAvailableInBlock,
                            costCents = costInBlock,
                            ratePerKwhCents = block.ratePerKwhCents
                        )
                    )
                    totalCostCents += costInBlock
                    remainingKwh -= kwhAvailableInBlock
                    currentKwhAccumulator += kwhAvailableInBlock
                }
            } else {
                val costInBlock = remainingKwh * block.ratePerKwhCents
                if (remainingKwh > 0) {
                    breakdown.add(
                        BlockYield(
                            blockIndex = index + 1,
                            kwhYield = remainingKwh,
                            costCents = costInBlock,
                            ratePerKwhCents = block.ratePerKwhCents
                        )
                    )
                    totalCostCents += costInBlock
                    currentKwhAccumulator += remainingKwh
                }
                remainingKwh = 0.0
                break
            }
        }

        val lastBlock = activePeriod.blocks.lastOrNull()
        val absoluteMaxKwh = if (lastBlock != null && lastBlock.maxKwh < 999999) lastBlock.maxKwh.toDouble() else null
        val effectiveTotalKwh = targetKwh - remainingKwh  // actual kWh that fit in blocks
        val isCapped = absoluteMaxKwh != null && remainingKwh > 1e-6

        return CalculationResult(
            totalKwh = effectiveTotalKwh,
            blockBreakdown = breakdown,
            fixedChargeCents = activePeriod.fixedMonthlyChargeCents,
            totalCostCents = totalCostCents,
            isCapped = isCapped,
            maxAvailableKwh = absoluteMaxKwh,
            errorMessage = if (isCapped) "Capped at ${absoluteMaxKwh?.toInt()} units — this provider does not allow purchases beyond this limit." else null
        )
    }

    /**
     * Calculates the breakdown for a cumulative kWh amount (already purchased).
     */
    fun calculateCumulativeBreakdown(
        provider: TariffProvider,
        cumulativeKwh: Double,
        date: LocalDate = LocalDate.now()
    ): List<BlockYield> {
        val activePeriod = findActivePeriod(provider, date) ?: return emptyList()
        
        var remainingKwh = cumulativeKwh
        var currentKwhAccumulator = 0.0
        val breakdown = mutableListOf<BlockYield>()

        for ((index, block) in activePeriod.blocks.withIndex()) {
            if (remainingKwh <= 0) break

            val maxAvailableKwhInBlock = block.maxKwh - currentKwhAccumulator
            val kwhInThisBlock = minOf(remainingKwh, maxAvailableKwhInBlock.toDouble())

            if (kwhInThisBlock > 0) {
                breakdown.add(
                    BlockYield(
                        blockIndex = index + 1,
                        kwhYield = kwhInThisBlock,
                        costCents = kwhInThisBlock * block.ratePerKwhCents,
                        ratePerKwhCents = block.ratePerKwhCents
                    )
                )
                remainingKwh -= kwhInThisBlock
                currentKwhAccumulator += kwhInThisBlock
            }
        }
        return breakdown
    }
}

data class CalculationResult(
    val totalKwh: Double,
    val blockBreakdown: List<BlockYield>,
    val fixedChargeCents: Int,
    val totalCostCents: Double = 0.0,
    val errorMessage: String? = null,
    val isCapped: Boolean = false,
    val maxAvailableKwh: Double? = null
)
