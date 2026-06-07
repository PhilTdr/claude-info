package dev.treder.info.claude.domain.model

data class ModelPricing(
    val inputPerMTok: Double,
    val outputPerMTok: Double,
    val cacheReadPerMTok: Double,
    val cacheCreate5mPerMTok: Double,
    val cacheCreate1hPerMTok: Double,
)
