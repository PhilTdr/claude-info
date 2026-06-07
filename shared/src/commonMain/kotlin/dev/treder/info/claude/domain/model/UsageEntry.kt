package dev.treder.info.claude.domain.model

import kotlin.time.Instant

data class UsageEntry(
    val timestamp: Instant,
    val model: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheCreation5mTokens: Long,
    val cacheCreation1hTokens: Long,
    val messageId: String? = null,
    val requestId: String? = null,
)
