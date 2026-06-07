package dev.treder.info.claude.domain.repository

import dev.treder.info.claude.domain.model.ModelPricing

interface PricingRepository {
    suspend fun pricingFor(model: String): ModelPricing
}
