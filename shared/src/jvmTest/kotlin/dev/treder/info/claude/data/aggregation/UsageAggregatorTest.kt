package dev.treder.info.claude.data.aggregation

import dev.treder.info.claude.domain.model.ModelPricing
import dev.treder.info.claude.domain.model.PricingTable
import dev.treder.info.claude.domain.model.UsageEntry
import dev.treder.info.claude.domain.model.YearMonth
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.test.assertNotNull

class UsageAggregatorTest {

    private val opusPricing = ModelPricing(
        inputPerMTok = 5.0,
        outputPerMTok = 25.0,
        cacheReadPerMTok = 0.5,
        cacheCreate5mPerMTok = 6.25,
        cacheCreate1hPerMTok = 10.0,
    )

    private val pricing = PricingTable(mapOf("claude-opus-4-7" to opusPricing))

    private val aggregator = UsageAggregator()
    private val utc = TimeZone.of("UTC")

    @Test
    fun aggregateDayFiltersEntriesOutsideTheDay() {
        val day = LocalDate(2026, 5, 4)
        val inDay = entry("2026-05-04T12:00:00Z", input = 1_000_000)
        val nextDay = entry("2026-05-05T00:00:01Z", input = 1_000_000)
        val previousDay = entry("2026-05-03T23:59:59Z", input = 1_000_000)

        val result = aggregator.aggregateDay(listOf(inDay, nextDay, previousDay), day, utc, pricing)

        assertEquals(1, result.byModel.size)
        assertEquals(1_000_000L, result.total?.inputTokens ?: 0L)
        assertCloseTo(5.0, result.total?.cost ?: 0.0)
    }

    @Test
    fun aggregateMonthIncludesDailyBreakdownAndSumsCorrectly() {
        val month = YearMonth(2026, Month.MAY)
        val entries = listOf(
            entry("2026-05-04T12:00:00Z", input = 1_000_000),
            entry("2026-05-05T12:00:00Z", input = 2_000_000),
            entry("2026-04-30T23:59:59Z", input = 99_999_999),
            entry("2026-06-01T00:00:00Z", input = 99_999_999),
        )

        val result = aggregator.aggregateMonth(entries, month, utc, pricing)

        assertNotNull(result)
        assertEquals(3_000_000L, result.total?.inputTokens ?: 0L)
        assertEquals(2, result.days.size)
        assertCloseTo(15.0, result.total?.cost ?: 0.0)
    }

    @Test
    fun costIncludesBothCache5mAnd1hWrites() {
        val day = LocalDate(2026, 5, 4)
        val entry = UsageEntry(
            timestamp = Instant.parse("2026-05-04T12:00:00Z"),
            model = "claude-opus-4-7",
            inputTokens = 0,
            outputTokens = 0,
            cacheReadTokens = 0,
            cacheCreation5mTokens = 1_000_000,
            cacheCreation1hTokens = 1_000_000,
        )

        val result = aggregator.aggregateDay(listOf(entry), day, utc, pricing)

        // 1M * $6.25 + 1M * $10 = $16.25
        assertCloseTo(16.25, result.total?.cost ?: 0.0)
    }

    @Test
    fun emptyEntriesProduceZeroTotals() {
        val day = LocalDate(2026, 5, 4)
        val result = aggregator.aggregateDay(emptyList(), day, utc, pricing)
        assertEquals(0L, result.total?.inputTokens ?: 0L)
        assertCloseTo(0.0, result.total?.cost ?: 0.0)
        assertEquals(0, result.byModel.size)
    }

    @Test
    fun unknownModelContributesZeroCost() {
        val day = LocalDate(2026, 5, 4)
        val entry = entry("2026-05-04T12:00:00Z", input = 1_000_000, model = "some-unlisted-model")

        val result = aggregator.aggregateDay(listOf(entry), day, utc, pricing)

        assertEquals(1_000_000L, result.total?.inputTokens ?: 0L)
        assertCloseTo(0.0, result.total?.cost ?: 0.0)
    }

    private fun entry(timestamp: String, input: Long = 0, output: Long = 0, model: String = "claude-opus-4-7") =
        UsageEntry(
            timestamp = Instant.parse(timestamp),
            model = model,
            inputTokens = input,
            outputTokens = output,
            cacheReadTokens = 0,
            cacheCreation5mTokens = 0,
            cacheCreation1hTokens = 0,
        )

    private fun assertCloseTo(expected: Double, actual: Double, epsilon: Double = 1e-4) {
        assertTrue(abs(expected - actual) < epsilon, "expected $expected to be close to $actual")
    }
}
