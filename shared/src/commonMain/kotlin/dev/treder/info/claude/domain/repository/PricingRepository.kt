package dev.treder.info.claude.domain.repository

import dev.treder.info.claude.domain.model.PricingState
import kotlinx.coroutines.flow.StateFlow

/**
 * Reactive source of model prices. The implementation fetches once on start and
 * then periodically, exposing the result as observable state. A failed refresh
 * never discards a previously loaded table.
 */
interface PricingRepository {

    /** The current pricing lifecycle state. */
    val state: StateFlow<PricingState>

    /** Triggers an immediate (re)fetch, e.g. for a user-initiated retry. */
    fun refresh()
}
