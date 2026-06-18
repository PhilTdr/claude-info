package dev.treder.info.claude.data.pricing

import dev.treder.info.claude.domain.model.ModelPricing

/**
 * Fetches the raw model→price map from a remote feed. Abstracted behind an
 * interface so the repository can be exercised without real network I/O.
 */
interface PricingApi {
    suspend fun fetch(): Map<String, ModelPricing>
}
