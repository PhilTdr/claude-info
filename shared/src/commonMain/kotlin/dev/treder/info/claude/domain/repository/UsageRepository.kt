package dev.treder.info.claude.domain.repository

import dev.treder.info.claude.domain.model.DayUsage
import dev.treder.info.claude.domain.model.MonthUsage
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    fun getTodayUsage(): Flow<DayUsage>
    fun getHistoryUsage(): Flow<List<MonthUsage>>
}
