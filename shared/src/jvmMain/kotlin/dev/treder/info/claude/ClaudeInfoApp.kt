package dev.treder.info.claude

import dev.treder.info.claude.data.aggregation.UsageAggregator
import dev.treder.info.claude.data.jsonl.JsonlUsageParser
import dev.treder.info.claude.data.pricing.LiteLlmPricingApi
import dev.treder.info.claude.data.repository.JsonlEntryCache
import dev.treder.info.claude.data.repository.JvmPricingRepository
import dev.treder.info.claude.data.repository.JvmUpdateRepository
import dev.treder.info.claude.data.auth.ClaudeAccessTokenProvider
import dev.treder.info.claude.data.auth.ClaudeOAuthRefreshApi
import dev.treder.info.claude.data.repository.JvmUsageLimitsRepository
import dev.treder.info.claude.data.repository.JvmUsageRepository
import dev.treder.info.claude.data.settings.ClaudeCredentialsStore
import dev.treder.info.claude.data.settings.ClaudeSettingsReader
import dev.treder.info.claude.data.update.GitHubReleaseApi
import dev.treder.info.claude.data.usagelimits.OAuthUsageApi
import dev.treder.info.claude.domain.repository.PricingRepository
import dev.treder.info.claude.domain.repository.UpdateRepository
import dev.treder.info.claude.domain.repository.UsageLimitsRepository
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
    private val credentialsStore = ClaudeCredentialsStore()

    // Hands out a valid OAuth access token, refreshing it via the stored refresh token when
    // it expires (and writing the new token back), so the limit bars keep working without a
    // manual re-login. Token source: ~/.claude/.credentials.json.
    private val accessTokenProvider = ClaudeAccessTokenProvider(
        readCredentials = { credentialsStore.read() },
        refresher = ClaudeOAuthRefreshApi(),
        persist = { access, refresh, expiresAt -> credentialsStore.writeRefreshed(access, refresh, expiresAt) },
    )

    // Polls the OAuth usage endpoint for the 5h / weekly limit bars, independent of the popup.
    private val usageLimitsRepository: UsageLimitsRepository = JvmUsageLimitsRepository(
        api = OAuthUsageApi(),
        accessTokenProvider = { accessTokenProvider.token() },
        scope = appScope,
    )

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
        usageLimitsRepository = usageLimitsRepository,
        preferredModelProvider = { settingsReader.readPreferredModel() },
    )
}
