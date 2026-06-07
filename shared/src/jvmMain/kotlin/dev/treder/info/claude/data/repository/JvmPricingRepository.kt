package dev.treder.info.claude.data.repository

import dev.treder.info.claude.data.pricing.LiteLlmPricingApi
import dev.treder.info.claude.data.pricing.PricingDefaults
import dev.treder.info.claude.domain.model.ModelPricing
import dev.treder.info.claude.domain.repository.PricingRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class JvmPricingRepository(
    private val api: LiteLlmPricingApi = LiteLlmPricingApi(),
) : PricingRepository {

    private val mutex = Mutex()

    @Volatile
    private var remote: Map<String, ModelPricing>? = null

    @Volatile
    private var attempted: Boolean = false

    override suspend fun pricingFor(model: String): ModelPricing {
        val map = ensureLoaded()
        map[model]?.let { return it }
        val stripped = stripDateSuffix(model)
        if (stripped != model) {
            map[stripped]?.let { return it }
        }
        // litellm sometimes uses "anthropic/claude-..." prefix
        map["anthropic/$model"]?.let { return it }
        map["anthropic/$stripped"]?.let { return it }
        return PricingDefaults.forModel(model)
    }

    private suspend fun ensureLoaded(): Map<String, ModelPricing> {
        remote?.let { return it }
        if (attempted) return emptyMap()
        return mutex.withLock {
            remote?.let { return@withLock it }
            attempted = true
            val loaded = runCatching { api.fetch() }.getOrElse { emptyMap() }
            remote = loaded
            loaded
        }
    }

    private fun stripDateSuffix(model: String): String =
        model.replace(Regex("-\\d{8}$"), "")
}
