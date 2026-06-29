package dev.treder.info.claude.domain.repository

import dev.treder.info.claude.domain.model.UsageLimit
import dev.treder.info.claude.domain.model.UsageLimitsStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface UsageLimitsRepository {
    fun getUsageLimits(): Flow<List<UsageLimit>>

    /** Drives the "limits temporarily unavailable" warning in the header. */
    val status: StateFlow<UsageLimitsStatus>
}
