package dev.treder.info.claude.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PricingTableTest {

    private val opus = ModelPricing(5.0, 25.0, 0.5, 6.25, 10.0)
    private val haiku = ModelPricing(1.0, 5.0, 0.1, 1.25, 2.0)

    @Test
    fun exactIdMatches() {
        val table = PricingTable(mapOf("claude-opus-4-7" to opus))
        assertEquals(opus, table.forModel("claude-opus-4-7"))
    }

    @Test
    fun trailingDateSuffixIsStripped() {
        val table = PricingTable(mapOf("claude-haiku-4-5" to haiku))
        assertEquals(haiku, table.forModel("claude-haiku-4-5-20251001"))
    }

    @Test
    fun anthropicPrefixIsResolved() {
        val table = PricingTable(mapOf("anthropic/claude-opus-4-7" to opus))
        assertEquals(opus, table.forModel("claude-opus-4-7"))
    }

    @Test
    fun anthropicPrefixWithDateSuffixIsResolved() {
        val table = PricingTable(mapOf("anthropic/claude-opus-4-7" to opus))
        assertEquals(opus, table.forModel("claude-opus-4-7-20251001"))
    }

    @Test
    fun unknownModelReturnsZero() {
        val table = PricingTable(mapOf("claude-opus-4-7" to opus))
        assertEquals(ModelPricing.ZERO, table.forModel("totally-made-up-model"))
    }
}
