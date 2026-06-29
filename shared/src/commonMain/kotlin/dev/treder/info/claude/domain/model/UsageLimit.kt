package dev.treder.info.claude.domain.model

import kotlinx.datetime.LocalDateTime

/**
 * A single usage-limit window shown above the statistics, e.g. the rolling
 * 5-hour session limit or the weekly limit.
 *
 * @property name human-readable label, e.g. "5-Stunden-Limit".
 * @property used consumed share of the window in 0f..1f.
 * @property resetAt local wall-clock time at which the window resets.
 */
data class UsageLimit(
    val name: String,
    val used: Float,
    val resetAt: LocalDateTime,
)
