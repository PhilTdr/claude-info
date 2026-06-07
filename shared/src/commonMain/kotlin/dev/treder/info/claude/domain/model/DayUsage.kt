package dev.treder.info.claude.domain.model

import kotlinx.datetime.*

data class DayUsage(
    val date: LocalDate,
    override val total: TokenUsage?,
    override val byModel: List<TokenUsage>,
) : PeriodUsage {
    override val from: LocalDateTime get() = date.atTime(0, 0)
    override val to: LocalDateTime get() = date.plus(1, DateTimeUnit.DAY).atTime(0, 0)
}
