package dev.treder.info.claude.domain.model

/**
 * Lifecycle of the live pricing feed.
 *
 * - [Loading]: the very first fetch is still in flight; no prices yet.
 * - [Ready]: prices are available. A failed refresh keeps the last [Ready]
 *   table in place, so this always holds the most recent successful fetch.
 * - [Failed]: the first fetch failed and there is nothing to fall back to.
 */
sealed interface PricingState {

    data object Loading : PricingState

    data class Ready(val table: PricingTable) : PricingState

    data object Failed : PricingState

    /** The current table, or `null` while loading or after a first-fetch failure. */
    fun tableOrNull(): PricingTable? = (this as? Ready)?.table
}
