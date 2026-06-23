package dev.treder.info.claude

import dev.treder.info.claude.data.aggregation.UsageAggregator
import dev.treder.info.claude.data.jsonl.JsonlUsageParser
import dev.treder.info.claude.data.pricing.LiteLlmPricingApi
import dev.treder.info.claude.data.repository.JsonlEntryCache
import dev.treder.info.claude.data.repository.JvmPricingRepository
import dev.treder.info.claude.data.repository.JvmUpdateRepository
import dev.treder.info.claude.data.repository.JvmUsageRepository
import dev.treder.info.claude.data.settings.ClaudeSettingsReader
import dev.treder.info.claude.data.update.GitHubReleaseApi
import dev.treder.info.claude.domain.repository.PricingRepository
import dev.treder.info.claude.domain.repository.UpdateRepository
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

    // Starts fetching prices immediately and keeps them fresh for the app's
    // lifetime, independent of whether the popup is open.
    private val pricingRepository: PricingRepository = JvmPricingRepository(
        api = pricingApi,
        scope = appScope,
    )
    private val aggregator = UsageAggregator()
    private val settingsReader = ClaudeSettingsReader()

    // Polls the GitHub release feed for newer versions, independent of the popup.
    private val updateRepository: UpdateRepository = JvmUpdateRepository(
        currentVersion = BuildConfig.APP_VERSION,
        api = GitHubReleaseApi(),
        scope = appScope,
    )

    val usageRepository: UsageRepository = JvmUsageRepository(
        cache = entryCache,
        aggregator = aggregator,
        pricing = pricingRepository,
        scope = appScope,
    )

    fun createViewModel(): UsageViewModel = UsageViewModel(
        usageRepository = usageRepository,
        pricingRepository = pricingRepository,
        updateRepository = updateRepository,
        preferredModelProvider = { settingsReader.readPreferredModel() },
    )
}
