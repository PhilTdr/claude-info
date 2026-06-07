package dev.treder.info.claude.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime


data class MonthUsage(
    val month: YearMonth,
    val days: List<DayUsage>,
    override val total: TokenUsage?,
    override val byModel: List<TokenUsage>,
) : PeriodUsage {
    override val from: LocalDateTime get() = month.atStartOfMonth().atTime(0, 0)
    override val to: LocalDateTime get() = month.next().atStartOfMonth().atTime(0, 0)
}
