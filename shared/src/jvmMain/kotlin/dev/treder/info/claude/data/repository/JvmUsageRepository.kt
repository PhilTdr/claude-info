package dev.treder.info.claude.data.repository

import dev.treder.info.claude.data.aggregation.UsageAggregator
import dev.treder.info.claude.domain.model.DayUsage
import dev.treder.info.claude.domain.model.MonthUsage
import dev.treder.info.claude.domain.model.PricingTable
import dev.treder.info.claude.domain.model.UsageEntry
import dev.treder.info.claude.domain.model.YearMonth
import dev.treder.info.claude.domain.repository.PricingRepository
import dev.treder.info.claude.domain.repository.UsageRepository
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class JvmUsageRepository(
    private val cache: JsonlEntryCache,
    private val aggregator: UsageAggregator,
    private val pricing: PricingRepository,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    scope: CoroutineScope,
    private val pollIntervalMillis: Long = 10_000L,
) : UsageRepository {

    private val entries: SharedFlow<List<UsageEntry>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(cache.refresh())
            delay(pollIntervalMillis.milliseconds)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    // No table yet (still loading or first fetch failed) means we deliberately
    // emit nothing — the UI stays in its loading/error state until prices arrive.
    private val pricingTable: Flow<PricingTable> =
        pricing.state.mapNotNull { it.tableOrNull() }

    override fun getTodayUsage(): Flow<DayUsage> =
        combine(entries, pricingTable) { snapshot, table ->
            aggregator.aggregateDay(snapshot, today(), zone, table)
        }.distinctUntilChanged()

    override fun getHistoryUsage(): Flow<List<MonthUsage>> =
        combine(entries, pricingTable) { snapshot, table ->
            // The full history, newest first. Entries are grouped by month once
            // and each month is aggregated from only its own entries; months
            // without data are simply absent, so the pager shows exactly the
            // months that exist. Future-dated entries (clock skew, corrupt
            // timestamps) are dropped so a bogus future month can't hijack the
            // newest slot and become the default page.
            val currentMonth = YearMonth.from(today())
            snapshot
                .groupBy { YearMonth.from(it.timestamp.toLocalDateTime(zone).date) }
                .entries
                .filter { it.key <= currentMonth }
                .sortedByDescending { it.key }
                .mapNotNull { (month, monthEntries) ->
                    aggregator.aggregateMonth(monthEntries, month, zone, table)
                }
        }.distinctUntilChanged()

    private fun today(): LocalDate = clock.now().toLocalDateTime(zone).date
}
