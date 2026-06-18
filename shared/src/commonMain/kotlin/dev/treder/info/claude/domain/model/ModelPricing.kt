package dev.treder.info.claude.domain.model

data class ModelPricing(
    val inputPerMTok: Double,
    val outputPerMTok: Double,
    val cacheReadPerMTok: Double,
    val cacheCreate5mPerMTok: Double,
    val cacheCreate1hPerMTok: Double,
) {
    companion object {
        /** Used for models the pricing feed does not list — contributes zero cost. */
        val ZERO: ModelPricing = ModelPricing(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}
