package dev.treder.info.claude

import dev.treder.info.claude.data.aggregation.UsageAggregator
import dev.treder.info.claude.data.jsonl.JsonlUsageParser
import dev.treder.info.claude.data.pricing.LiteLlmPricingApi
import dev.treder.info.claude.data.repository.JsonlEntryCache
import dev.treder.info.claude.data.repository.JvmPricingRepository
import dev.treder.info.claude.data.repository.JvmUsageRepository
import dev.treder.info.claude.data.settings.ClaudeSettingsReader
import dev.treder.info.claude.domain.repository.PricingRepository
import dev.treder.info.claude.domain.repository.UsageRepository
import dev.treder.info.claude.presentation.UsageViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Composition root for the desktop app. Wires the JVM data layer to the
 * shared presentation layer. There are intentionally no use cases here —
 * ViewModels consume repositories directly.
 */
class ClaudeInfoApp {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val parser = JsonlUsageParser()
    private val entryCache = JsonlEntryCache(parser)
    private val pricingApi = LiteLlmPricingApi()
    private val pricingRepository: PricingRepository = JvmPricingRepository(pricingApi)
    private val aggregator = UsageAggregator(pricingRepository)
    private val settingsReader = ClaudeSettingsReader()

    val usageRepository: UsageRepository = JvmUsageRepository(
        cache = entryCache,
        aggregator = aggregator,
        scope = appScope,
    )

    fun createViewModel(): UsageViewModel = UsageViewModel(
        repository = usageRepository,
        preferredModelProvider = { settingsReader.readPreferredModel() },
    )
}
