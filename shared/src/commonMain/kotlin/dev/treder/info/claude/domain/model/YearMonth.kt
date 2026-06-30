package dev.treder.info.claude.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

data class YearMonth(val year: Int, val month: Month) : Comparable<YearMonth> {

    fun atStartOfMonth(): LocalDate = LocalDate(year, month, 1)

    fun next(): YearMonth {
        val nextMonthOrdinal = month.ordinal + 1
        return if (nextMonthOrdinal >= 12) {
            YearMonth(year + 1, Month.JANUARY)
        } else {
            YearMonth(year, Month.entries[nextMonthOrdinal])
        }
    }

    override fun compareTo(other: YearMonth): Int =
        compareValuesBy(this, other, { it.year }, { it.month.ordinal })

    companion object {
        fun from(date: LocalDate): YearMonth = YearMonth(date.year, date.month)
    }
}
