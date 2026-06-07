package dev.treder.info.claude.domain.model

import kotlinx.datetime.LocalDateTime

sealed interface PeriodUsage {
    val total: TokenUsage?
    val byModel: List<TokenUsage>
    val from: LocalDateTime
    val to: LocalDateTime
}
