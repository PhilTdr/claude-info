package dev.treder.info.claude.domain.model

/**
 * Immutable snapshot of model prices keyed by model id, as fetched from the
 * pricing feed. Encapsulates the name-matching quirks so callers just ask for a
 * model and get a price back.
 */
data class PricingTable(private val byModel: Map<String, ModelPricing>) {

    val size: Int get() = byModel.size

    val isEmpty: Boolean get() = byModel.isEmpty()

    /**
     * Resolves the price for a model id, tolerating the variations seen in the
     * wild: a trailing date suffix (`-20251001`) and LiteLLM's `anthropic/`
     * prefix. Returns [ModelPricing.ZERO] for models the feed does not list, so
     * unknown models still show their token counts at zero cost.
     */
    fun forModel(model: String): ModelPricing {
        byModel[model]?.let { return it }

        val stripped = model.replace(DATE_SUFFIX, "")
        if (stripped != model) {
            byModel[stripped]?.let { return it }
        }
        byModel["anthropic/$model"]?.let { return it }
        byModel["anthropic/$stripped"]?.let { return it }

        return ModelPricing.ZERO
    }

    companion object {
        private val DATE_SUFFIX = Regex("-\\d{8}$")
    }
}
