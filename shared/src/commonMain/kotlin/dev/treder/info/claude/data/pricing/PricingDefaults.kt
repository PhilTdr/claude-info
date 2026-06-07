package dev.treder.info.claude.data.pricing

import dev.treder.info.claude.domain.model.ModelPricing

/**
 * Anthropic listed prices in USD per million tokens. Used as fallback when
 * the LiteLLM pricing feed is unreachable, or for models not present there.
 */
object PricingDefaults {

    val OPUS_4_7: ModelPricing = ModelPricing(
        inputPerMTok = 5.0,
        outputPerMTok = 25.0,
        cacheReadPerMTok = 0.50,
        cacheCreate5mPerMTok = 6.25,
        cacheCreate1hPerMTok = 10.0,
    )

    val SONNET_4_6: ModelPricing = ModelPricing(
        inputPerMTok = 3.0,
        outputPerMTok = 15.0,
        cacheReadPerMTok = 0.30,
        cacheCreate5mPerMTok = 3.75,
        cacheCreate1hPerMTok = 6.0,
    )

    val HAIKU_4_5: ModelPricing = ModelPricing(
        inputPerMTok = 1.0,
        outputPerMTok = 5.0,
        cacheReadPerMTok = 0.10,
        cacheCreate5mPerMTok = 1.25,
        cacheCreate1hPerMTok = 2.0,
    )

    val UNKNOWN: ModelPricing = ModelPricing(0.0, 0.0, 0.0, 0.0, 0.0)

    fun forModel(model: String): ModelPricing {
        val lower = model.lowercase()
        return when {
            "opus-4-7" in lower || "opus-4.7" in lower -> OPUS_4_7
            "sonnet-4-6" in lower || "sonnet-4.6" in lower -> SONNET_4_6
            "haiku-4-5" in lower || "haiku-4.5" in lower -> HAIKU_4_5
            "opus" in lower -> OPUS_4_7
            "sonnet" in lower -> SONNET_4_6
            "haiku" in lower -> HAIKU_4_5
            else -> UNKNOWN
        }
    }
}
