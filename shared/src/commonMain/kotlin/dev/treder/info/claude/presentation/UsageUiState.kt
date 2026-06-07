package dev.treder.info.claude.presentation

import dev.treder.info.claude.domain.model.DayUsage
import dev.treder.info.claude.domain.model.MonthUsage

data class UsageUiState(
    val today: DayUsage? = null,
    val history: List<MonthUsage>? = null,
    val preferredModel: String? = null,
    val error: String? = null,
)
