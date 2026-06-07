package dev.treder.info.claude.data.pricing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PricingDefaultsTest {

    @Test
    fun opusModelResolves() {
        assertEquals(PricingDefaults.OPUS_4_7, PricingDefaults.forModel("claude-opus-4-7"))
    }

    @Test
    fun sonnetModelResolves() {
        assertEquals(PricingDefaults.SONNET_4_6, PricingDefaults.forModel("claude-sonnet-4-6"))
    }

    @Test
    fun haikuModelWithDateSuffixResolves() {
        assertEquals(PricingDefaults.HAIKU_4_5, PricingDefaults.forModel("claude-haiku-4-5-20251001"))
    }

    @Test
    fun unknownModelReturnsZeroPricing() {
        val result = PricingDefaults.forModel("totally-made-up-model")
        assertEquals(PricingDefaults.UNKNOWN, result)
        assertNotEquals(PricingDefaults.OPUS_4_7, result)
    }
}
