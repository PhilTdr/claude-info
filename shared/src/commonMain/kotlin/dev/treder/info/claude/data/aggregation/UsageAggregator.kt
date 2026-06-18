package dev.treder.info.claude.data.aggregation

import dev.treder.info.claude.domain.model.*
import dev.treder.info.claude.domain.model.PricingTable
import dev.treder.info.claude.domain.model.YearMonth
import kotlinx.datetime.*
import kotlin.time.Instant

/**
 * Pure aggregation of usage entries into per-day / per-month totals. Prices are
 * passed in as a [PricingTable] snapshot, so this class has no I/O and no state.
 */
class UsageAggregator {

    fun aggregateDay(entries: List<UsageEntry>, date: LocalDate, zone: TimeZone, pricing: PricingTable): DayUsage {
        val from = date.atStartOfDayIn(zone)
        val to = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        val byModel = aggregateRange(entries, from, to, pricing)
        return DayUsage(
            date = date,
            total = sumTotal(byModel),
            byModel = byModel,
        )
    }

    fun aggregateMonth(entries: List<UsageEntry>, month: YearMonth, zone: TimeZone, pricing: PricingTable): MonthUsage? {
        if (entries.isEmpty()) return null

        val from = month.atStartOfMonth().atStartOfDayIn(zone)
        val to = month.next().atStartOfMonth().atStartOfDayIn(zone)
        val byModel = aggregateRange(entries, from, to, pricing)

        val activeDays = entries.asSequence()
            .filter { it.timestamp >= from && it.timestamp < to }
            .map { it.timestamp.toLocalDateTime(zone).date }
            .distinct()
            .sortedDescending()
            .toList()
        if (activeDays.isEmpty()) return null
        val days = activeDays.map { aggregateDay(entries, it, zone, pricing) }

        return MonthUsage(
            month = month,
            days = days,
            total = sumTotal(byModel),
            byModel = byModel,
        )
    }

    private fun aggregateRange(entries: List<UsageEntry>, from: Instant, to: Instant, pricing: PricingTable): List<TokenUsage> {
        val inRange = entries.filter { it.timestamp >= from && it.timestamp < to }
        if (inRange.isEmpty()) return emptyList()

        val grouped = inRange.groupBy { it.model }
        return grouped.map { (model, list) -> aggregateModel(model, list, pricing) }
            .sortedByDescending { it.inputTokens + it.outputTokens + it.cacheWriteTokens + it.cacheReadTokens }
    }

    private fun aggregateModel(model: String, list: List<UsageEntry>, pricing: PricingTable): TokenUsage {
        var input = 0L
        var output = 0L
        var cacheRead = 0L
        var c5m = 0L
        var c1h = 0L
        for (e in list) {
            input += e.inputTokens
            output += e.outputTokens
            cacheRead += e.cacheReadTokens
            c5m += e.cacheCreation5mTokens
            c1h += e.cacheCreation1hTokens
        }
        val price = pricing.forModel(model)
        val costUsd = (input * price.inputPerMTok +
                output * price.outputPerMTok +
                cacheRead * price.cacheReadPerMTok +
                c5m * price.cacheCreate5mPerMTok +
                c1h * price.cacheCreate1hPerMTok) / 1_000_000.0
        return TokenUsage(
            model = model,
            inputTokens = input,
            outputTokens = output,
            cacheReadTokens = cacheRead,
            cacheCreation5mTokens = c5m,
            cacheCreation1hTokens = c1h,
            cost = costUsd,
        )
    }

    private fun sumTotal(byModel: List<TokenUsage>): TokenUsage? {
        if (byModel.isEmpty()) return null
        return TokenUsage(
            model = null,
            inputTokens = byModel.sumOf { it.inputTokens },
            outputTokens = byModel.sumOf { it.outputTokens },
            cacheReadTokens = byModel.sumOf { it.cacheReadTokens },
            cacheCreation5mTokens = byModel.sumOf { it.cacheCreation5mTokens },
            cacheCreation1hTokens = byModel.sumOf { it.cacheCreation1hTokens },
            cost = byModel.sumOf { it.cost },
        )
    }
}
