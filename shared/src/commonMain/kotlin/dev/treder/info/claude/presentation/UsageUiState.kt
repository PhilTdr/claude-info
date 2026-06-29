package dev.treder.info.claude.presentation

import dev.treder.info.claude.domain.model.DayUsage
import dev.treder.info.claude.domain.model.MonthUsage
import dev.treder.info.claude.domain.model.UpdateStatus
import dev.treder.info.claude.domain.model.UsageLimit
import dev.treder.info.claude.domain.model.UsageLimitsStatus

/** What the UI needs to know about the pricing feed, decoupled from the data layer. */
enum class PricingPhase {
    /** First fetch still running — nothing to show yet. */
    Loading,

    /** Prices are available; usage numbers can be rendered. */
    Ready,

    /** First fetch failed with no prior data — show an info/retry state. */
    Failed,
}

data class UsageUiState(
    val pricingPhase: PricingPhase = PricingPhase.Loading,
    val today: DayUsage? = null,
    val history: List<MonthUsage>? = null,
    val preferredModel: String? = null,
    val error: String? = null,
    /** Update availability — drives the title-bar update hint. */
    val updateStatus: UpdateStatus = UpdateStatus.NoUpdate,
    /** Usage-limit bars shown above the statistics; empty hides the section. */
    val usageLimits: List<UsageLimit> = emptyList(),
    /** Availability of the limit feed — drives the header warning icon and its message. */
    val usageLimitsStatus: UsageLimitsStatus = UsageLimitsStatus.Loading,
) {
    /** True once prices and both usage sections have loaded — the happy-path render. */
    val isReady: Boolean
        get() = pricingPhase == PricingPhase.Ready && today != null && history != null

    /**
     * Whether to render the dashboard instead of the loading spinner. Once prices
     * are available we leave the spinner as soon as the usage sections arrive — or
     * a usage error occurs, so a read error can never trap the UI on the spinner.
     */
    val showDashboard: Boolean
        get() = pricingPhase == PricingPhase.Ready && (isReady || error != null)
}
