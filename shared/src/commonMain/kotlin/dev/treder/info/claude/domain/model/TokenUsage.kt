package dev.treder.info.claude.domain.model

data class TokenUsage(
    val model: String?,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheCreation5mTokens: Long,
    val cacheCreation1hTokens: Long,
    val cost: Double,
) {
    val cacheWriteTokens: Long get() = cacheCreation5mTokens + cacheCreation1hTokens
}
