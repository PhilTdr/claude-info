package dev.treder.info.claude.data.repository

import dev.treder.info.claude.data.aggregation.UsageAggregator
import dev.treder.info.claude.domain.model.DayUsage
import dev.treder.info.claude.domain.model.MonthUsage
import dev.treder.info.claude.domain.model.UsageEntry
import dev.treder.info.claude.domain.model.YearMonth
import dev.treder.info.claude.domain.repository.UsageRepository
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class JvmUsageRepository(
    private val cache: JsonlEntryCache,
    private val aggregator: UsageAggregator,
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

    override fun getTodayUsage(): Flow<DayUsage> =
        entries.map { aggregator.aggregateDay(it, today(), zone) }
            .distinctUntilChanged()

    override fun getHistoryUsage(): Flow<List<MonthUsage>> =
        entries.map { snapshot ->
            val month = currentMonth()
            (0..2L).mapNotNull { offset ->
                aggregator.aggregateMonth(snapshot, month.minusMonths(offset), zone)
            }
        }.distinctUntilChanged()

    private fun today(): LocalDate = clock.now().toLocalDateTime(zone).date
    private fun currentMonth(): YearMonth = YearMonth.from(today())
}
