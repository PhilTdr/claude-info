package dev.treder.info.claude.data.repository

import dev.treder.info.claude.data.pricing.LiteLlmPricingApi
import dev.treder.info.claude.data.pricing.PricingApi
import dev.treder.info.claude.domain.model.PricingState
import dev.treder.info.claude.domain.model.PricingTable
import dev.treder.info.claude.domain.repository.PricingRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps a live [PricingTable] by fetching from [api] on start and every
 * [refreshInterval] thereafter. The contract: a fetch failure never throws away
 * a table we already have — the previous successful fetch stays in place. Only a
 * failed *first* fetch (nothing to fall back to) surfaces as [PricingState.Failed].
 *
 * The refresh loop runs for the lifetime of [scope]; a [refresh] call wakes it
 * early without disturbing the periodic cadence.
 */
class JvmPricingRepository(
    private val api: PricingApi = LiteLlmPricingApi(),
    scope: CoroutineScope,
    private val refreshInterval: Duration = 60.minutes,
) : PricingRepository {

    private val _state = MutableStateFlow<PricingState>(PricingState.Loading)
    override val state: StateFlow<PricingState> = _state.asStateFlow()

    // Conflated: a burst of retry taps collapses into a single pending wake-up.
    private val refreshSignal = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            while (isActive) {
                loadOnce()
                // Sleep until the next interval, or until a manual refresh wakes us.
                withTimeoutOrNull(refreshInterval) { refreshSignal.receive() }
            }
        }
    }

    override fun refresh() {
        refreshSignal.trySend(Unit)
    }

    private suspend fun loadOnce() {
        runCatching {
            val raw = api.fetch()
            check(raw.isNotEmpty()) { "pricing feed returned no usable entries" }
            PricingTable(raw)
        }.onSuccess { table ->
            _state.value = PricingState.Ready(table)
        }.onFailure {
            // Let cancellation propagate; runCatching would otherwise swallow it.
            if (it is CancellationException) throw it
            // Fall back to the previous fetch: only report failure when we have
            // never had a table to show.
            if (_state.value !is PricingState.Ready) {
                _state.value = PricingState.Failed
            }
        }
    }
}
