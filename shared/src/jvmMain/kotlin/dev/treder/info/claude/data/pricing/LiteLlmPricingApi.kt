package dev.treder.info.claude.data.pricing

import dev.treder.info.claude.domain.model.ModelPricing
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class LiteLlmPricingApi(
    private val client: HttpClient = defaultClient(),
    private val url: String = LITE_LLM_URL,
    private val json: Json = parserJson,
) : PricingApi {

    override suspend fun fetch(): Map<String, ModelPricing> {
        val response: HttpResponse = client.get(url)
        val text = response.bodyAsText()
        val raw: Map<String, LiteLlmModelEntry> = json.decodeFromString(text)
        return raw.mapNotNull { (model, entry) ->
            val pricing = entry.toModelPricing() ?: return@mapNotNull null
            model to pricing
        }.toMap()
    }

    @Serializable
    internal data class LiteLlmModelEntry(
        @SerialName("input_cost_per_token") val inputCostPerToken: Double? = null,
        @SerialName("output_cost_per_token") val outputCostPerToken: Double? = null,
        @SerialName("cache_read_input_token_cost") val cacheReadInputTokenCost: Double? = null,
        @SerialName("cache_creation_input_token_cost") val cacheCreationInputTokenCost: Double? = null,
        @SerialName("cache_creation_input_token_cost_above_1hr") val cacheCreationInputTokenCostAbove1hr: Double? = null,
    ) {
        fun toModelPricing(): ModelPricing? {
            val input = inputCostPerToken ?: return null
            val output = outputCostPerToken ?: return null
            // Anthropic's tier ratios relative to the base input rate:
            //   5m cache write = 1.25x, 1h cache write = 2.0x.
            // LiteLLM occasionally publishes 0.0 instead of omitting a field (e.g.
            // claude-sonnet-4-6 currently has cache_creation_input_token_cost_above_1hr=0).
            // Treat 0.0 as missing and fall back to the derived rate; `?:` alone would
            // accept the bogus 0.0 and silently undercount cost.
            val cache5m = cacheCreationInputTokenCost?.takeIf { it > 0.0 } ?: (input * 1.25)
            val cache1h = cacheCreationInputTokenCostAbove1hr?.takeIf { it > 0.0 } ?: (input * 2.0)
            return ModelPricing(
                inputPerMTok = input * 1_000_000.0,
                outputPerMTok = output * 1_000_000.0,
                cacheReadPerMTok = (cacheReadInputTokenCost ?: 0.0) * 1_000_000.0,
                cacheCreate5mPerMTok = cache5m * 1_000_000.0,
                cacheCreate1hPerMTok = cache1h * 1_000_000.0,
            )
        }
    }

    companion object {
        const val LITE_LLM_URL =
            "https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json"

        private val parserJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(parserJson) }
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
            expectSuccess = true
        }
    }
}
