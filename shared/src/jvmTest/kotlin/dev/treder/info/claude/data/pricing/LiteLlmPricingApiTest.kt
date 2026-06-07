package dev.treder.info.claude.data.pricing

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiteLlmPricingApiTest {

    @Test
    fun cache1hFallsBackToTwiceInputWhenFieldMissing() {
        // Opus-style entry with no above_1hr field at all.
        val entry = LiteLlmPricingApi.LiteLlmModelEntry(
            inputCostPerToken = 0.000005,
            outputCostPerToken = 0.000025,
            cacheReadInputTokenCost = 0.0000005,
            cacheCreationInputTokenCost = 0.00000625,
            cacheCreationInputTokenCostAbove1hr = null,
        )
        val pricing = entry.toModelPricing()
        assertNotNull(pricing)
        // 1h = input * 2.0 = $10/Mtok
        assertCloseTo(10.0, pricing.cacheCreate1hPerMTok)
        // 5m = input * 1.25 fallback (here actual value $6.25 from the field)
        assertCloseTo(6.25, pricing.cacheCreate5mPerMTok)
    }

    @Test
    fun cache1hRecoversWhenLiteLlmReportsZero() {
        // Sonnet-4-6-style: LiteLLM currently publishes 0 for the 1h field.
        val entry = LiteLlmPricingApi.LiteLlmModelEntry(
            inputCostPerToken = 0.000003,
            outputCostPerToken = 0.000015,
            cacheReadInputTokenCost = 0.0000003,
            cacheCreationInputTokenCost = 0.00000375,
            cacheCreationInputTokenCostAbove1hr = 0.0,
        )
        val pricing = entry.toModelPricing()
        assertNotNull(pricing)
        // Must not believe the 0.0 — fall back to input * 2.0 = $6/Mtok.
        assertCloseTo(6.0, pricing.cacheCreate1hPerMTok)
    }

    @Test
    fun cache5mFallsBackWhenZeroOrMissing() {
        val entry = LiteLlmPricingApi.LiteLlmModelEntry(
            inputCostPerToken = 0.000001,
            outputCostPerToken = 0.000005,
            cacheReadInputTokenCost = null,
            cacheCreationInputTokenCost = 0.0,
            cacheCreationInputTokenCostAbove1hr = 0.000002,
        )
        val pricing = entry.toModelPricing()
        assertNotNull(pricing)
        // 5m = input * 1.25 = $1.25/Mtok
        assertCloseTo(1.25, pricing.cacheCreate5mPerMTok)
        // 1h was reported correctly
        assertCloseTo(2.0, pricing.cacheCreate1hPerMTok)
    }

    private fun assertCloseTo(expected: Double, actual: Double, epsilon: Double = 1e-6) {
        assertTrue(abs(expected - actual) < epsilon, "expected $expected, got $actual")
    }
}
